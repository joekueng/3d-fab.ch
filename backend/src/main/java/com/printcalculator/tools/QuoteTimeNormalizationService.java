package com.printcalculator.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.tools.QuoteTimeCurve;
import com.printcalculator.service.ProfileManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Offline timing experiment. Not a Spring service and never called by the website calculator. */
public class QuoteTimeNormalizationService {
    private static final Logger logger=Logger.getLogger(QuoteTimeNormalizationService.class.getName());
    private final boolean enabled;
    private final Calibration calibration;
    private final ObjectMapper mapper;
    private final ProfileManager profiles;
    private final Map<String,String> profileHashes=new ConcurrentHashMap<>();
    private final Map<String,Boolean> verifiedRules=new ConcurrentHashMap<>();

    public QuoteTimeNormalizationService(ObjectMapper mapper,
                                        boolean enabled,
                                        ProfileManager profiles) {
        this.enabled=enabled;this.mapper=mapper;this.profiles=profiles;
        try(var input=getClass().getResourceAsStream("/quote-time/p2s-a1-v2.json")) {
            if(input==null)throw new IOException("Missing quote time calibration");
            calibration=mapper.readValue(input,Calibration.class);
            if(calibration.schemaVersion()!=2 || calibration.version()==null || calibration.rules()==null || calibration.rules().isEmpty()) {
                throw new IOException("Incomplete quote time calibration");
            }
            for(Rule rule:calibration.rules())rule.validate();
        }catch(IOException|IllegalArgumentException ex){throw new IllegalStateException("Invalid quote time calibration",ex);}
    }

    /** Offline replay of recorded statistics; live services also verify profile fingerprints. */
    public QuoteTimeNormalizationService(ObjectMapper mapper,boolean enabled){this(mapper,enabled,null);}

    public NormalizedTime normalize(CalibrationPrintStats raw,String machine,String filament,String material,BigDecimal nozzle,BigDecimal layer) {
        return normalize(raw,machine,filament,material,nozzle,layer,null,"standard");
    }

    public NormalizedTime normalize(CalibrationPrintStats raw,String machine,String filament,String material,BigDecimal nozzle,
                                    BigDecimal layer,String process,String quality) {
        if(!enabled || raw.printTimeSeconds()<=0 || raw.modelPrintTimeSeconds()==null || raw.modelPrintTimeSeconds()<=0
                || raw.modelPrintTimeSeconds()>raw.printTimeSeconds() || raw.layerCount()==null || raw.layerCount()<=0
                || nozzle==null || layer==null) return nativeTime(raw,machine);
        String code=material==null?"":material.trim().toUpperCase(Locale.ROOT);
        String qualityKey=qualityKey(quality);
        Rule rule=null;
        for(Rule candidate:calibration.rules()) {
            if(candidate.material().equals(code) && candidate.p2sMachine().equals(machine) && candidate.p2sFilament().equals(filament)
                    && candidate.nozzleMm().compareTo(nozzle)==0 && candidate.layerMm().compareTo(layer)==0
                    && candidate.qualities().contains(qualityKey) && (process==null||candidate.p2sProcess().equals(process))) {
                if(rule!=null)return nativeTime(raw,machine); // Never choose between ambiguous reference processes.
                rule=candidate;
            }
        }
        if(rule==null || !verifiedRules.computeIfAbsent(rule.id(),ignored->profilesMatch(candidateRule(ignored)))) {
            return nativeTime(raw,machine);
        }
        long model=rule.curve().modelSeconds(raw.modelPrintTimeSeconds(),raw.layerCount());
        long seconds=Math.addExact(model,rule.curve().startupSeconds());
        String formatted=seconds/3600+"h "+seconds%3600/60+"m "+seconds%60+"s";
        var quoted=new CalibrationPrintStats(seconds,formatted,raw.filamentWeightGrams(),raw.filamentLengthMm(),model,raw.layerCount());
        BigDecimal factor=BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(raw.printTimeSeconds()),8,RoundingMode.HALF_UP);
        return new NormalizedTime(quoted,raw,factor,calibration.version(),calibration.reference(),rule.id());
    }

    private Rule candidateRule(String id){return calibration.rules().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();}

    private boolean profilesMatch(Rule rule) {
        if(profiles==null)return true;
        boolean matches=matches(rule,"a1Machine",rule.a1Machine(),"machine") && matches(rule,"p2sMachine",rule.p2sMachine(),"machine")
                && matches(rule,"a1Filament",rule.a1Filament(),"filament") && matches(rule,"p2sFilament",rule.p2sFilament(),"filament")
                && matches(rule,"a1Process",rule.a1Process(),"process") && matches(rule,"p2sProcess",rule.p2sProcess(),"process");
        if(!matches)logger.warning("Quote timing calibration no longer matches printer/filament/process profiles: "+rule.id());
        return matches;
    }

    private boolean matches(Rule rule,String key,String name,String type) {
        String actual=profileHashes.computeIfAbsent(type+":"+name,ignored->{
            try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(profiles.getMergedProfile(name,type))));}
            catch(Exception ex){logger.warning("Cannot verify quote timing profile: "+name);return "";}
        });
        return actual.equals(rule.profileHashes().get(key));
    }

    private static String qualityKey(String quality) {
        return switch(quality==null?"":quality.trim().toLowerCase(Locale.ROOT)) {
            case "draft"->"draft";
            case "extra_fine","high","high_definition"->"extra_fine";
            default->"standard";
        };
    }
    private static NormalizedTime nativeTime(CalibrationPrintStats raw,String machine){
        return new NormalizedTime(raw,raw,BigDecimal.ONE,"native",machine,"native");
    }
    public record NormalizedTime(CalibrationPrintStats quoteStats,CalibrationPrintStats nativeStats,BigDecimal factor,
                                 String calibrationVersion,String reference,String ruleId) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    private record Calibration(int schemaVersion,String version,String reference,List<Rule> rules) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    private record Rule(String id,String material,BigDecimal nozzleMm,BigDecimal layerMm,String a1Machine,String p2sMachine,
                        String a1Filament,String p2sFilament,String a1Process,String p2sProcess,List<String> qualities,
                        Map<String,String> profileHashes,QuoteTimeCurve curve) {
        void validate(){
            if(id==null||material==null||nozzleMm==null||layerMm==null||a1Machine==null||p2sMachine==null||a1Filament==null
                    ||p2sFilament==null||a1Process==null||p2sProcess==null||qualities==null||qualities.isEmpty()
                    ||profileHashes==null||profileHashes.size()!=6||curve==null)throw new IllegalArgumentException("Incomplete timing rule");
        }
    }
}
