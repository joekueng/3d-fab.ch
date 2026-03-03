package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.model.PrintStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class SlicerService {

    private static final Logger logger = Logger.getLogger(SlicerService.class.getName());
    private static final Pattern SIZE_X_PATTERN = Pattern.compile("(?m)^\\s*size_x\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Y_PATTERN = Pattern.compile("(?m)^\\s*size_y\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Z_PATTERN = Pattern.compile("(?m)^\\s*size_z\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");

    private final String trustedSlicerPath;
    private final String trustedAssimpPath;
    private final ProfileManager profileManager;
    private final GCodeParser gCodeParser;
    private final ObjectMapper mapper;

    public SlicerService(
            @Value("${slicer.path}") String slicerPath,
            @Value("${assimp.path:assimp}") String assimpPath,
            ProfileManager profileManager,
            GCodeParser gCodeParser,
            ObjectMapper mapper) {
        this.trustedSlicerPath = normalizeExecutablePath(slicerPath);
        this.trustedAssimpPath = normalizeExecutablePath(assimpPath);
        this.profileManager = profileManager;
        this.gCodeParser = gCodeParser;
        this.mapper = mapper;
    }

    public PrintStats slice(File inputStl, String machineName, String filamentName, String processName,
                            Map<String, String> machineOverrides, Map<String, String> processOverrides) throws IOException {
        // 1. Prepare Profiles
        ObjectNode machineProfile = profileManager.getMergedProfile(machineName, "machine");
        ObjectNode filamentProfile = profileManager.getMergedProfile(filamentName, "filament");
        ObjectNode processProfile = profileManager.getMergedProfile(processName, "process");

        logger.info("Slicer profiles: machine='" + machineName + "', filament='" + filamentName + "', process='" + processName + "'");
        logger.info("Machine limits: printable_area=" + machineProfile.path("printable_area")
                + ", printable_height=" + machineProfile.path("printable_height")
                + ", bed_exclude_area=" + machineProfile.path("bed_exclude_area")
                + ", head_wrap_detect_zone=" + machineProfile.path("head_wrap_detect_zone"));

        // Apply Overrides
        if (machineOverrides != null) {
            machineOverrides.forEach(machineProfile::put);
        }
        if (processOverrides != null) {
            processOverrides.forEach(processProfile::put);
        }

        // 2. Create Temp Dir
        Path tempDir = Files.createTempDirectory("slicer_job_");
        try {
            File mFile = tempDir.resolve("machine.json").toFile();
            File fFile = tempDir.resolve("filament.json").toFile();
            File pFile = tempDir.resolve("process.json").toFile();

            mapper.writeValue(mFile, machineProfile);
            mapper.writeValue(fFile, filamentProfile);
            mapper.writeValue(pFile, processProfile);

            String basename = inputStl.getName();
            if (basename.toLowerCase().endsWith(".stl")) {
                basename = basename.substring(0, basename.length() - 4);
            }
            Path slicerLogPath = tempDir.resolve("orcaslicer.log");
            String machineProfilePath = requireSafeArgument(mFile.getAbsolutePath(), "machine profile path");
            String processProfilePath = requireSafeArgument(pFile.getAbsolutePath(), "process profile path");
            String filamentProfilePath = requireSafeArgument(fFile.getAbsolutePath(), "filament profile path");
            String outputDirPath = requireSafeArgument(tempDir.toAbsolutePath().toString(), "output directory path");
            String inputModelPath = requireSafeArgument(inputStl.getAbsolutePath(), "input model path");
            List<String> slicerInputPaths = resolveSlicerInputPaths(inputStl, inputModelPath, tempDir);

            // 3. Run slicer. Retry with arrange only for out-of-volume style failures.
            for (boolean useArrange : new boolean[]{false, true}) {
                // Build process arguments explicitly to avoid shell interpretation and command injection.
                ProcessBuilder pb = new ProcessBuilder();
                List<String> command = pb.command();
                command.add(trustedSlicerPath);
                command.add("--load-settings");
                command.add(machineProfilePath);
                command.add("--load-settings");
                command.add(processProfilePath);
                command.add("--load-filaments");
                command.add(filamentProfilePath);
                command.add("--ensure-on-bed");
                if (useArrange) {
                    command.add("--arrange");
                    command.add("1");
                }
                command.add("--slice");
                command.add("0");
                command.add("--outputdir");
                command.add(outputDirPath);
                command.addAll(slicerInputPaths);

                logger.info("Executing Slicer" + (useArrange ? " (retry with arrange)" : "") + ": " + String.join(" ", command));

                Files.deleteIfExists(slicerLogPath);
                pb.directory(tempDir.toFile());
                pb.redirectErrorStream(true);
                pb.redirectOutput(slicerLogPath.toFile());

                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.MINUTES);

                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("Slicer timed out");
                }

                if (process.exitValue() != 0) {
                    String error = "";
                    if (Files.exists(slicerLogPath)) {
                        error = Files.readString(slicerLogPath, StandardCharsets.UTF_8);
                    }
                    if (!useArrange && isOutOfVolumeError(error)) {
                        logger.warning("Slicer reported model out of printable area, retrying with arrange.");
                        continue;
                    }
                    throw new IOException("Slicer failed with exit code " + process.exitValue() + ": " + error);
                }

                File gcodeFile = tempDir.resolve(basename + ".gcode").toFile();
                if (!gcodeFile.exists()) {
                    File alt = tempDir.resolve("plate_1.gcode").toFile();
                    if (alt.exists()) {
                        gcodeFile = alt;
                    } else {
                        throw new IOException("GCode output not found in " + tempDir);
                    }
                }

                return gCodeParser.parse(gcodeFile);
            }

            throw new IOException("Slicer failed after retry");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during slicing", e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public Optional<ModelDimensions> inspectModelDimensions(File inputModel) {
            Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("slicer_info_");
            Path infoLogPath = tempDir.resolve("orcaslicer-info.log");
            String inputModelPath = requireSafeArgument(inputModel.getAbsolutePath(), "input model path");

            ProcessBuilder pb = new ProcessBuilder();
            List<String> infoCommand = pb.command();
            infoCommand.add(trustedSlicerPath);
            infoCommand.add("--info");
            infoCommand.add(inputModelPath);
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(infoLogPath.toFile());

            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.warning("Model info extraction timed out for " + inputModel.getName());
                return Optional.empty();
            }

            String output = Files.exists(infoLogPath)
                    ? Files.readString(infoLogPath, StandardCharsets.UTF_8)
                    : "";

            if (process.exitValue() != 0) {
                logger.warning("OrcaSlicer --info failed (exit " + process.exitValue() + ") for "
                        + inputModel.getName() + ": " + output);
                return Optional.empty();
            }

            Optional<ModelDimensions> parsed = parseModelDimensionsFromInfoOutput(output);
            if (parsed.isEmpty()) {
                logger.warning("Could not parse size_x/size_y/size_z from OrcaSlicer --info output for "
                        + inputModel.getName() + ": " + output);
            }
            return parsed;
        } catch (Exception e) {
            logger.warning("Failed to inspect model dimensions for " + inputModel.getName() + ": " + e.getMessage());
            return Optional.empty();
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    static Optional<ModelDimensions> parseModelDimensionsFromInfoOutput(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }

        Double x = extractDouble(SIZE_X_PATTERN, output);
        Double y = extractDouble(SIZE_Y_PATTERN, output);
        Double z = extractDouble(SIZE_Z_PATTERN, output);

        if (x == null || y == null || z == null) {
            return Optional.empty();
        }

        if (x <= 0 || y <= 0 || z <= 0) {
            return Optional.empty();
        }

        return Optional.of(new ModelDimensions(x, y, z));
    }

    private static Double extractDouble(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warning("Failed to delete temp path " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("Failed to walk temp directory " + path + ": " + e.getMessage());
        }
    }

    private boolean isOutOfVolumeError(String errorLog) {
        if (errorLog == null || errorLog.isBlank()) {
            return false;
        }

        String normalized = errorLog.toLowerCase();
        return normalized.contains("nothing to be sliced")
                || normalized.contains("no object is fully inside the print volume")
                || normalized.contains("calc_exclude_triangles");
    }

    private List<String> resolveSlicerInputPaths(File inputModel, String inputModelPath, Path tempDir)
            throws IOException, InterruptedException {
        if (!inputModel.getName().toLowerCase().endsWith(".3mf")) {
            return List.of(inputModelPath);
        }

        List<String> convertedStlPaths = convert3mfToStlInputPaths(inputModel, tempDir);
        logger.info("Converted 3MF to " + convertedStlPaths.size() + " STL file(s) for slicing.");
        return convertedStlPaths;
    }

    private List<String> convert3mfToStlInputPaths(File input3mf, Path tempDir) throws IOException, InterruptedException {
        Path conversionOutputDir = tempDir.resolve("converted-from-3mf");
        Files.createDirectories(conversionOutputDir);

        String conversionOutputStlPath = requireSafeArgument(
                conversionOutputDir.resolve("converted.stl").toAbsolutePath().toString(),
                "3MF conversion output STL path"
        );
        String conversionOutputObjPath = requireSafeArgument(
                conversionOutputDir.resolve("converted.obj").toAbsolutePath().toString(),
                "3MF conversion output OBJ path"
        );
        String input3mfPath = requireSafeArgument(input3mf.getAbsolutePath(), "input 3MF path");

        Path convertedStl = Path.of(conversionOutputStlPath);
        String stlLog = runAssimpExport(input3mfPath, conversionOutputStlPath, tempDir.resolve("assimp-convert-stl.log"));
        if (hasRenderableGeometry(convertedStl)) {
            return List.of(convertedStl.toString());
        }

        logger.warning("Assimp STL conversion produced empty geometry. Retrying conversion to OBJ.");

        Path convertedObj = Path.of(conversionOutputObjPath);
        String objLog = runAssimpExport(input3mfPath, conversionOutputObjPath, tempDir.resolve("assimp-convert-obj.log"));
        if (hasRenderableGeometry(convertedObj)) {
            return List.of(convertedObj.toString());
        }

        Path fallbackStl = conversionOutputDir.resolve("converted-fallback.stl");
        long fallbackTriangles = convert3mfArchiveToAsciiStl(input3mf.toPath(), fallbackStl);
        if (fallbackTriangles > 0 && hasRenderableGeometry(fallbackStl)) {
            logger.warning("Assimp conversion produced empty geometry. Fallback 3MF XML extractor generated "
                    + fallbackTriangles + " triangles.");
            return List.of(fallbackStl.toString());
        }

        throw new IOException("3MF conversion produced no renderable geometry (STL+OBJ). STL log: "
                + stlLog + " OBJ log: " + objLog);
    }

    private String runAssimpExport(String input3mfPath, String outputModelPath, Path conversionLogPath)
            throws IOException, InterruptedException {
        ProcessBuilder conversionPb = new ProcessBuilder();
        List<String> conversionCommand = conversionPb.command();
        conversionCommand.add(trustedAssimpPath);
        conversionCommand.add("export");
        conversionCommand.add(input3mfPath);
        conversionCommand.add(outputModelPath);

        logger.info("Converting 3MF with Assimp: " + String.join(" ", conversionCommand));

        Files.deleteIfExists(conversionLogPath);
        conversionPb.redirectErrorStream(true);
        conversionPb.redirectOutput(conversionLogPath.toFile());

        Process conversionProcess = conversionPb.start();
        boolean conversionFinished = conversionProcess.waitFor(3, TimeUnit.MINUTES);
        if (!conversionFinished) {
            conversionProcess.destroyForcibly();
            throw new IOException("3MF conversion timed out");
        }

        String conversionLog = Files.exists(conversionLogPath)
                ? Files.readString(conversionLogPath, StandardCharsets.UTF_8)
                : "";
        if (conversionProcess.exitValue() != 0) {
            throw new IOException("3MF conversion failed with exit code "
                    + conversionProcess.exitValue() + ": " + conversionLog);
        }
        return conversionLog;
    }

    private boolean hasRenderableGeometry(Path modelPath) throws IOException {
        if (!Files.isRegularFile(modelPath) || Files.size(modelPath) == 0) {
            return false;
        }

        String fileName = modelPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".obj")) {
            try (var lines = Files.lines(modelPath)) {
                return lines.map(String::trim).anyMatch(line -> line.startsWith("f "));
            }
        }

        if (fileName.endsWith(".stl")) {
            long size = Files.size(modelPath);
            if (size <= 84) {
                return false;
            }
            byte[] header = new byte[84];
            try (InputStream is = Files.newInputStream(modelPath)) {
                int read = is.read(header);
                if (read < 84) {
                    return false;
                }
            }
            long triangleCount = ((long) (header[80] & 0xff))
                    | (((long) (header[81] & 0xff)) << 8)
                    | (((long) (header[82] & 0xff)) << 16)
                    | (((long) (header[83] & 0xff)) << 24);
            if (triangleCount > 0) {
                return true;
            }
            try (var lines = Files.lines(modelPath)) {
                return lines.limit(2000).anyMatch(line -> line.contains("facet normal"));
            }
        }

        return true;
    }

    private long convert3mfArchiveToAsciiStl(Path input3mf, Path outputStl) throws IOException {
        Map<String, ThreeMfModelDocument> modelCache = new HashMap<>();
        long[] triangleCount = new long[]{0L};

        try (ZipFile zipFile = new ZipFile(input3mf.toFile());
             BufferedWriter writer = Files.newBufferedWriter(outputStl, StandardCharsets.UTF_8)) {
            writer.write("solid converted\n");

            ThreeMfModelDocument rootModel = loadThreeMfModel(zipFile, modelCache, "3D/3dmodel.model");
            Element build = findFirstChildByLocalName(rootModel.rootElement(), "build");
            if (build == null) {
                throw new IOException("3MF build section not found in root model");
            }

            for (Element item : findChildrenByLocalName(build, "item")) {
                if ("0".equals(getAttributeByLocalName(item, "printable"))) {
                    continue;
                }
                String objectId = getAttributeByLocalName(item, "objectid");
                if (objectId == null || objectId.isBlank()) {
                    continue;
                }
                Transform itemTransform = parseTransform(getAttributeByLocalName(item, "transform"));
                writeObjectTriangles(
                        zipFile,
                        modelCache,
                        rootModel.modelPath(),
                        objectId,
                        itemTransform,
                        writer,
                        triangleCount,
                        new HashSet<>(),
                        0
                );
            }

            writer.write("endsolid converted\n");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("3MF fallback conversion failed: " + e.getMessage(), e);
        }

        return triangleCount[0];
    }

    private void writeObjectTriangles(
            ZipFile zipFile,
            Map<String, ThreeMfModelDocument> modelCache,
            String modelPath,
            String objectId,
            Transform transform,
            BufferedWriter writer,
            long[] triangleCount,
            Set<String> recursionGuard,
            int depth
    ) throws Exception {
        if (depth > 64) {
            throw new IOException("3MF component nesting too deep");
        }

        String guardKey = modelPath + "#" + objectId;
        if (!recursionGuard.add(guardKey)) {
            return;
        }

        try {
            ThreeMfModelDocument modelDocument = loadThreeMfModel(zipFile, modelCache, modelPath);
            Element objectElement = modelDocument.objectsById().get(objectId);
            if (objectElement == null) {
                return;
            }

            Element mesh = findFirstChildByLocalName(objectElement, "mesh");
            if (mesh != null) {
                writeMeshTriangles(mesh, transform, writer, triangleCount);
            }

            Element components = findFirstChildByLocalName(objectElement, "components");
            if (components != null) {
                for (Element component : findChildrenByLocalName(components, "component")) {
                    String childObjectId = getAttributeByLocalName(component, "objectid");
                    if (childObjectId == null || childObjectId.isBlank()) {
                        continue;
                    }
                    String componentPath = getAttributeByLocalName(component, "path");
                    String resolvedModelPath = (componentPath == null || componentPath.isBlank())
                            ? modelDocument.modelPath()
                            : normalizeZipPath(componentPath);
                    Transform componentTransform = parseTransform(getAttributeByLocalName(component, "transform"));
                    Transform combinedTransform = transform.multiply(componentTransform);

                    writeObjectTriangles(
                            zipFile,
                            modelCache,
                            resolvedModelPath,
                            childObjectId,
                            combinedTransform,
                            writer,
                            triangleCount,
                            recursionGuard,
                            depth + 1
                    );
                }
            }
        } finally {
            recursionGuard.remove(guardKey);
        }
    }

    private void writeMeshTriangles(
            Element meshElement,
            Transform transform,
            BufferedWriter writer,
            long[] triangleCount
    ) throws IOException {
        Element verticesElement = findFirstChildByLocalName(meshElement, "vertices");
        Element trianglesElement = findFirstChildByLocalName(meshElement, "triangles");
        if (verticesElement == null || trianglesElement == null) {
            return;
        }

        List<Vec3> vertices = new java.util.ArrayList<>();
        for (Element vertex : findChildrenByLocalName(verticesElement, "vertex")) {
            Double x = parseDoubleAttribute(vertex, "x");
            Double y = parseDoubleAttribute(vertex, "y");
            Double z = parseDoubleAttribute(vertex, "z");
            if (x == null || y == null || z == null) {
                continue;
            }
            vertices.add(new Vec3(x, y, z));
        }

        if (vertices.isEmpty()) {
            return;
        }

        for (Element triangle : findChildrenByLocalName(trianglesElement, "triangle")) {
            Integer v1 = parseIntAttribute(triangle, "v1");
            Integer v2 = parseIntAttribute(triangle, "v2");
            Integer v3 = parseIntAttribute(triangle, "v3");
            if (v1 == null || v2 == null || v3 == null) {
                continue;
            }
            if (v1 < 0 || v2 < 0 || v3 < 0 || v1 >= vertices.size() || v2 >= vertices.size() || v3 >= vertices.size()) {
                continue;
            }

            Vec3 p1 = transform.apply(vertices.get(v1));
            Vec3 p2 = transform.apply(vertices.get(v2));
            Vec3 p3 = transform.apply(vertices.get(v3));
            writeAsciiFacet(writer, p1, p2, p3);
            triangleCount[0]++;
        }
    }

    private void writeAsciiFacet(BufferedWriter writer, Vec3 p1, Vec3 p2, Vec3 p3) throws IOException {
        Vec3 normal = computeNormal(p1, p2, p3);
        writer.write("facet normal " + normal.x() + " " + normal.y() + " " + normal.z() + "\n");
        writer.write(" outer loop\n");
        writer.write("  vertex " + p1.x() + " " + p1.y() + " " + p1.z() + "\n");
        writer.write("  vertex " + p2.x() + " " + p2.y() + " " + p2.z() + "\n");
        writer.write("  vertex " + p3.x() + " " + p3.y() + " " + p3.z() + "\n");
        writer.write(" endloop\n");
        writer.write("endfacet\n");
    }

    private Vec3 computeNormal(Vec3 a, Vec3 b, Vec3 c) {
        double ux = b.x() - a.x();
        double uy = b.y() - a.y();
        double uz = b.z() - a.z();
        double vx = c.x() - a.x();
        double vy = c.y() - a.y();
        double vz = c.z() - a.z();

        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 1e-12) {
            return new Vec3(0.0, 0.0, 0.0);
        }
        return new Vec3(nx / length, ny / length, nz / length);
    }

    private ThreeMfModelDocument loadThreeMfModel(
            ZipFile zipFile,
            Map<String, ThreeMfModelDocument> modelCache,
            String modelPath
    ) throws Exception {
        String normalizedPath = normalizeZipPath(modelPath);
        ThreeMfModelDocument cached = modelCache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }

        ZipEntry entry = zipFile.getEntry(normalizedPath);
        if (entry == null) {
            throw new IOException("3MF model entry not found: " + normalizedPath);
        }

        Document document = parseXmlDocument(zipFile, entry);
        Element root = document.getDocumentElement();
        Map<String, Element> objectsById = new HashMap<>();
        Element resources = findFirstChildByLocalName(root, "resources");
        if (resources != null) {
            for (Element objectElement : findChildrenByLocalName(resources, "object")) {
                String id = getAttributeByLocalName(objectElement, "id");
                if (id != null && !id.isBlank()) {
                    objectsById.put(id, objectElement);
                }
            }
        }

        ThreeMfModelDocument loaded = new ThreeMfModelDocument(normalizedPath, root, objectsById);
        modelCache.put(normalizedPath, loaded);
        return loaded;
    }

    private Document parseXmlDocument(ZipFile zipFile, ZipEntry entry) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        disableIfSupported(dbf, "http://apache.org/xml/features/disallow-doctype-decl");
        disableIfSupported(dbf, "http://xml.org/sax/features/external-general-entities");
        disableIfSupported(dbf, "http://xml.org/sax/features/external-parameter-entities");
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        try (InputStream is = zipFile.getInputStream(entry)) {
            return dbf.newDocumentBuilder().parse(is);
        }
    }

    private void disableIfSupported(DocumentBuilderFactory dbf, String feature) {
        try {
            dbf.setFeature(feature, false);
        } catch (Exception ignored) {
            // Best-effort hardening.
        }
    }

    private String normalizeZipPath(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IOException("Invalid empty 3MF model path");
        }
        String normalized = rawPath.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IOException("Invalid 3MF model path: " + rawPath);
        }
        return normalized;
    }

    private List<Element> findChildrenByLocalName(Element parent, String localName) {
        List<Element> result = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String nodeLocalName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
                if (localName.equals(nodeLocalName)) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private Element findFirstChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String nodeLocalName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
                if (localName.equals(nodeLocalName)) {
                    return element;
                }
            }
        }
        return null;
    }

    private String getAttributeByLocalName(Element element, String localName) {
        if (element.hasAttribute(localName)) {
            return element.getAttribute(localName);
        }
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrLocal = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            if (localName.equals(attrLocal)) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    private Double parseDoubleAttribute(Element element, String attributeName) {
        String value = getAttributeByLocalName(element, attributeName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseIntAttribute(Element element, String attributeName) {
        String value = getAttributeByLocalName(element, attributeName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Transform parseTransform(String rawTransform) throws IOException {
        if (rawTransform == null || rawTransform.isBlank()) {
            return Transform.identity();
        }
        String[] tokens = rawTransform.trim().split("\\s+");
        if (tokens.length != 12) {
            throw new IOException("Invalid 3MF transform format: " + rawTransform);
        }
        double[] v = new double[12];
        for (int i = 0; i < 12; i++) {
            try {
                v[i] = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid number in 3MF transform: " + rawTransform, e);
            }
        }
        return new Transform(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11]);
    }

    private record ThreeMfModelDocument(String modelPath, Element rootElement, Map<String, Element> objectsById) {
    }

    private record Vec3(double x, double y, double z) {
    }

    private record Transform(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22,
            double tx, double ty, double tz
    ) {
        static Transform identity() {
            return new Transform(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0);
        }

        Transform multiply(Transform other) {
            return new Transform(
                    m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
                    m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
                    m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
                    m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
                    m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
                    m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
                    m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
                    m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
                    m20 * other.m02 + m21 * other.m12 + m22 * other.m22,
                    m00 * other.tx + m01 * other.ty + m02 * other.tz + tx,
                    m10 * other.tx + m11 * other.ty + m12 * other.tz + ty,
                    m20 * other.tx + m21 * other.ty + m22 * other.tz + tz
            );
        }

        Vec3 apply(Vec3 v) {
            return new Vec3(
                    m00 * v.x() + m01 * v.y() + m02 * v.z() + tx,
                    m10 * v.x() + m11 * v.y() + m12 * v.z() + ty,
                    m20 * v.x() + m21 * v.y() + m22 * v.z() + tz
            );
        }
    }

    private String normalizeExecutablePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("slicer.path is required");
        }
        if (containsControlChars(configuredPath)) {
            throw new IllegalArgumentException("slicer.path contains invalid control characters");
        }
        try {
            return Path.of(configuredPath.trim()).normalize().toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid slicer.path: " + configuredPath, e);
        }
    }

    private String requireSafeArgument(String value, String argName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required argument: " + argName);
        }
        if (containsControlChars(value)) {
            throw new IOException("Invalid control characters in " + argName);
        }
        return value;
    }

    private boolean containsControlChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\0' || ch == '\n' || ch == '\r') {
                return true;
            }
        }
        return false;
    }
}
