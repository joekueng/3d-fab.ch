package com.printcalculator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.tools.A1TimeReference;
import com.printcalculator.service.MaterialPrintCompatibilityService;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.StepConversionService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resumable sequential comparisons using a read-only export of calculator configuration. */
public final class QuoteTimeCalibrationMatrix {
    private QuoteTimeCalibrationMatrix() {}
    public record Combination(String id, String material, BigDecimal nozzleMm, BigDecimal layerMm,
                              String a1Machine, String p2sMachine, String a1Filament, String p2sFilament,
                              String a1Process, String p2sProcess, List<String> qualities,
                              Map<String,String> profileHashes) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: inventory.json work-directory [--representative] [--plan|--pilot|--check]");
        Logger.getLogger(ProfileManager.class.getName()).setLevel(Level.WARNING);
        var mapper = new ObjectMapper();
        var profiles = new CachedProfiles(System.getenv().getOrDefault("PROFILES_DIR", "profiles"), mapper);
        var inventory = mapper.readTree(Path.of(args[0]).toFile());
        if(inventory.path("variantOverrideCount").asInt()>0) {
            throw new IllegalArgumentException("Export contains variant-specific filament overrides: add explicit entries before calibration");
        }
        Path directory = Path.of(args[1]).toAbsolutePath();Files.createDirectories(directory);
        var combinations = combinations(inventory, profiles, mapper);
        var fixtures = CalibrationFixtures.create(directory.resolve("fixtures"));
        if (List.of(args).contains("--representative")) {
            fixtures = fixtures.stream().filter(f -> List.of("cube80", "block120", "tube80", "cube65", "cylinder60", "plate150").contains(f.id())).toList();
        }
        var plan = new LinkedHashMap<String,Object>();
        plan.put("inventorySha256", hash(Files.readAllBytes(Path.of(args[0]))));
        plan.put("combinations", combinations);
        plan.put("fixtures", fixtures.stream().map(f -> Map.of("id", f.id(), "group", f.group())).toList());
        write(mapper,directory.resolve("plan.json"),plan);
        System.out.println("PLAN combinations="+combinations.size()+", slices="+(combinations.size()*fixtures.size()*2));
        if (List.of(args).contains("--plan")) return;
        boolean pilot = List.of(args).contains("--pilot");
        boolean check = List.of(args).contains("--check");
        String runtime = System.getenv().getOrDefault("CALIBRATION_RUNTIME",System.getProperty("os.name")+"/"+System.getProperty("os.arch"));
        String executable = System.getenv().getOrDefault("SLICER_PATH",System.getProperty("os.name").startsWith("Mac")
                ? "/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer" : "/opt/orcaslicer/AppRun");
        var slicer = new CalibrationSlicer(executable,"assimp",profiles,mapper,new StepConversionService("python3",120));
        Path reportFile=directory.resolve("results.json");
        var measurements = new LinkedHashMap<String,Map<String,Object>>();
        if (Files.exists(reportFile)) {
            JsonNode saved=mapper.readTree(reportFile.toFile());
            if (!runtime.equals(saved.path("runtime").asText()) || !plan.get("inventorySha256").equals(saved.path("inventorySha256").asText())) {
                throw new IllegalArgumentException("Runtime or inventory changed: use a new output directory");
            }
            if(!saved.path("combinations").equals(mapper.valueToTree(combinations))) {
                throw new IllegalArgumentException("Profiles or compatibility changed: use a new output directory");
            }
            for (JsonNode row : saved.path("measurements")) {
                Map<String,Object> entry=mapper.convertValue(row,new com.fasterxml.jackson.core.type.TypeReference<>(){});
                measurements.put(row.path("key").asText(),entry);
            }
        }
        int completed=0;
        for (var combination : combinations) {
            for (var fixture : fixtures) {
                if (pilot && !fixture.id().equals("cube20")) continue;
                if (check && !fixture.id().equals("rod5")) continue;
                String key=combination.id()+":"+fixture.id();
                Map<String,Object> entry=measurements.computeIfAbsent(key,ignored -> {
                    var value=new LinkedHashMap<String,Object>();value.put("key",key);value.put("combination",combination.id());
                    value.put("model",fixture.id());value.put("group",fixture.group());return value;
                });
                entry.put("group",fixture.group());
                if (entry.containsKey("failure")) break;
                for (String machine : List.of("a1","p2s")) {
                    if (entry.containsKey(machine)) continue;
                    long started=System.nanoTime();
                    try {
                        CalibrationPrintStats stats=slicer.slice(fixture.path().toFile(),machine.equals("a1") ? combination.a1Machine():combination.p2sMachine(),
                                machine.equals("a1")?combination.a1Filament():combination.p2sFilament(),
                                machine.equals("a1")?combination.a1Process():combination.p2sProcess(),null,
                                Map.of("layer_height",combination.layerMm().stripTrailingZeros().toPlainString(),
                                        "sparse_infill_density","15%","sparse_infill_pattern","grid","enable_support","1"));
                        if (stats.modelPrintTimeSeconds()==null || stats.layerCount()==null || stats.printTimeSeconds()<=0) {
                            throw new IllegalStateException("Missing total/model/layer timing metadata");
                        }
                        entry.put(machine,stats);
                        System.out.printf(java.util.Locale.ROOT,"MEASURE %s %s %s %s %s %s seconds=%d wall=%.2f%n",
                                combination.id(),combination.material(),combination.nozzleMm(),combination.layerMm(),fixture.id(),machine,
                                stats.printTimeSeconds(),(System.nanoTime()-started)/1e9);
                    } catch (Exception ex) {
                        entry.put("failure",machine+": "+ex.getClass().getSimpleName()+": "+ex.getMessage());
                        System.out.println("FAIL "+combination.id()+" "+entry.get("failure"));
                    }
                    var report=new LinkedHashMap<String,Object>();
                    report.put("createdAt",Instant.now().toString());report.put("runtime",runtime);report.putAll(plan);
                    report.put("measurements",measurements.values());write(mapper,reportFile,report);
                    if (entry.containsKey("failure")) break;
                }
                if (entry.containsKey("failure")) break;
            }
            completed++;
            System.out.println("PROGRESS configurations="+completed+"/"+combinations.size());
        }
        System.out.println("DONE "+reportFile);
    }

    static List<Combination> combinations(JsonNode inventory,ProfileManager profiles,ObjectMapper mapper) throws Exception {
        var result = new LinkedHashMap<String,Combination>();
        var compatibility=new MaterialPrintCompatibilityService();
        for (JsonNode a1 : inventory.path("machines")) {
            if (!a1.path("profile").asText().startsWith("Bambu Lab A1 ")) continue;
            BigDecimal nozzle=a1.path("nozzle").decimalValue();
            JsonNode p2s=null;
            for (JsonNode candidate : inventory.path("machines")) {
                if (candidate.path("profile").asText().startsWith("Bambu Lab P2S ")
                        && candidate.path("nozzle").decimalValue().compareTo(nozzle)==0) {p2s=candidate;break;}
            }
            if(p2s==null)continue;
            String a1Machine=a1.path("profile").asText(),p2sMachine=p2s.path("profile").asText();
            for(JsonNode material:inventory.path("materials")) {
                String code=material.path("code").asText();
                if (!A1TimeReference.supports(code, nozzle)) continue;
                var type=new FilamentMaterialType();type.setMaterialCode(code);type.setIsTechnical(material.path("technical").asBoolean());
                var variant=new FilamentVariant();variant.setFilamentMaterialType(type);
                for(JsonNode policy:inventory.path("layers")) {
                    if(policy.path("nozzle").decimalValue().compareTo(nozzle)!=0)continue;
                    BigDecimal layer=policy.path("layer").decimalValue();
                    try {compatibility.validate(variant,nozzle,layer);} catch(org.springframework.web.server.ResponseStatusException ignored){continue;}
                    for(String quality:List.of("draft","standard","extra_fine")) {
                        var a1Process=profiles.findCompatibleProcessProfileName(a1Machine,layer,quality);
                        var p2sProcess=profiles.findCompatibleProcessProfileName(p2sMachine,layer,quality);
                        if(a1Process.isEmpty()||p2sProcess.isEmpty())continue;
                        String a1Filament=filament(inventory,a1.path("id").asLong(),code);
                        String p2sFilament=filament(inventory,p2s.path("id").asLong(),code);
                        var hashes=new LinkedHashMap<String,String>();
                        hashes.put("a1Machine",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(a1Machine,"machine"))));
                        hashes.put("p2sMachine",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(p2sMachine,"machine"))));
                        hashes.put("a1Filament",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(a1Filament,"filament"))));
                        hashes.put("p2sFilament",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(p2sFilament,"filament"))));
                        hashes.put("a1Process",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(a1Process.get(),"process"))));
                        hashes.put("p2sProcess",hash(mapper.writeValueAsBytes(profiles.getMergedProfile(p2sProcess.get(),"process"))));
                        String id=hash(mapper.writeValueAsBytes(List.of(code,nozzle.stripTrailingZeros().toPlainString(),
                                layer.stripTrailingZeros().toPlainString(),a1Machine,p2sMachine,a1Filament,p2sFilament,
                                a1Process.get(),p2sProcess.get(),hashes))).substring(0,16);
                        var existing=result.get(id);
                        if(existing!=null){existing.qualities().add(quality);continue;}
                        result.put(id,new Combination(id,code,nozzle,layer,a1Machine,p2sMachine,a1Filament,p2sFilament,
                                a1Process.get(),p2sProcess.get(),new ArrayList<>(List.of(quality)),hashes));
                    }
                }
            }
        }
        return List.copyOf(result.values());
    }

    private static String filament(JsonNode inventory,long machine,String material) {
        for(JsonNode mapping:inventory.path("mappings")) {
            if(mapping.path("machine").asLong()==machine && mapping.path("material").asText().equals(material))return mapping.path("filament").asText();
        }
        // Snapshot of runtime fallbacks: analysis must not change the website resolver.
        return switch (material.toUpperCase(java.util.Locale.ROOT)) {
            case "PLA TOUGH" -> "Bambu PLA Tough @BBL A1";
            case "PETG" -> "Generic PETG";
            case "TPU" -> "Generic TPU";
            case "PC" -> "Generic PC";
            case "ABS" -> "Generic ABS";
            default -> "Generic PLA";
        };
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    /** Cache only for the offline run; every caller receives an independent mutable job profile. */
    private static final class CachedProfiles extends ProfileManager {
        private final Map<String,com.fasterxml.jackson.databind.node.ObjectNode> cache=new LinkedHashMap<>();
        CachedProfiles(String root,ObjectMapper mapper){super(root,mapper);}
        @Override
        public com.fasterxml.jackson.databind.node.ObjectNode getMergedProfile(String name,String type) throws java.io.IOException {
            String key=type+":"+name;
            var value=cache.get(key);
            if(value==null){value=super.getMergedProfile(name,type);cache.put(key,value);}
            return value.deepCopy();
        }
    }
    private static void write(ObjectMapper mapper,Path output,Object value) throws Exception {
        Path temporary=output.resolveSibling(output.getFileName()+".tmp");mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),value);
        Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
}
