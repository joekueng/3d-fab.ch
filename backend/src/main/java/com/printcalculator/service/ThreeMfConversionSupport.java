package com.printcalculator.service;

import com.printcalculator.exception.ModelProcessingException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiImportFile;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_PreTransformVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_SortByPType;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

final class ThreeMfConversionSupport {
    private static final Logger logger = Logger.getLogger(ThreeMfConversionSupport.class.getName());

    private final String trustedAssimpPath;

    ThreeMfConversionSupport(String trustedAssimpPath) {
        this.trustedAssimpPath = trustedAssimpPath;
    }

    Path convert3mfToPersistentStl(File input3mf, Path destinationStl) throws IOException {
        Path tempDir = Files.createTempDirectory("slicer_convert_");
        try {
            List<String> convertedPaths = convert3mfToStlInputPaths(input3mf, tempDir);
            if (convertedPaths.isEmpty()) {
                throw new ModelProcessingException(
                        "MODEL_CONVERSION_FAILED",
                        "Unable to process this 3MF file. Try another format or contact us directly via Request Consultation."
                );
            }
            Path source = Path.of(convertedPaths.get(0));
            Path parent = destinationStl.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destinationStl, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return destinationStl;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during 3MF conversion", e);
        } finally {
            SlicerFileSupport.deleteRecursively(tempDir, logger);
        }
    }

    List<String> convert3mfToStlInputPaths(File input3mf, Path tempDir) throws IOException, InterruptedException {
        Path conversionOutputDir = tempDir.resolve("converted-from-3mf");
        Files.createDirectories(conversionOutputDir);

        String conversionOutputStlPath = SlicerFileSupport.requireSafeArgument(
                conversionOutputDir.resolve("converted.stl").toAbsolutePath().toString(),
                "3MF conversion output STL path"
        );
        String conversionOutputObjPath = SlicerFileSupport.requireSafeArgument(
                conversionOutputDir.resolve("converted.obj").toAbsolutePath().toString(),
                "3MF conversion output OBJ path"
        );
        String input3mfPath = SlicerFileSupport.requireSafeArgument(input3mf.getAbsolutePath(), "input 3MF path");

        String stlLog = "";
        String objLog = "";

        Path lwjglConvertedStl = conversionOutputDir.resolve("converted-lwjgl.stl");
        try {
            long lwjglTriangles = convert3mfToStlWithLwjglAssimp(input3mf.toPath(), lwjglConvertedStl);
            if (lwjglTriangles > 0 && hasRenderableGeometry(lwjglConvertedStl)) {
                logger.info("Converted 3MF to STL via LWJGL Assimp. Triangles: " + lwjglTriangles);
                return List.of(lwjglConvertedStl.toString());
            }
            logger.warning("LWJGL Assimp conversion produced no renderable geometry.");
        } catch (Exception | LinkageError e) {
            logger.warning("LWJGL Assimp conversion failed, falling back to assimp CLI: " + e.getMessage());
        }

        Path convertedStl = Path.of(conversionOutputStlPath);
        try {
            stlLog = runAssimpExport(input3mfPath, conversionOutputStlPath, tempDir.resolve("assimp-convert-stl.log"));
            if (hasRenderableGeometry(convertedStl)) {
                return List.of(convertedStl.toString());
            }
            logger.warning("Assimp STL conversion produced empty geometry.");
        } catch (IOException e) {
            stlLog = e.getMessage() != null ? e.getMessage() : "";
            logger.warning("Assimp STL conversion failed, trying alternate conversion paths: " + stlLog);
        }

        logger.warning("Retrying 3MF conversion to OBJ.");

        Path convertedObj = Path.of(conversionOutputObjPath);
        try {
            objLog = runAssimpExport(input3mfPath, conversionOutputObjPath, tempDir.resolve("assimp-convert-obj.log"));
            if (hasRenderableGeometry(convertedObj)) {
                Path stlFromObj = conversionOutputDir.resolve("converted-from-obj.stl");
                runAssimpExport(
                        convertedObj.toString(),
                        stlFromObj.toString(),
                        tempDir.resolve("assimp-convert-obj-to-stl.log")
                );
                if (hasRenderableGeometry(stlFromObj)) {
                    return List.of(stlFromObj.toString());
                }
                logger.warning("Assimp OBJ->STL conversion produced empty geometry.");
            }
            logger.warning("Assimp OBJ conversion produced empty geometry.");
        } catch (IOException e) {
            objLog = e.getMessage() != null ? e.getMessage() : "";
            logger.warning("Assimp OBJ conversion failed: " + objLog);
        }

        Path fallbackStl = conversionOutputDir.resolve("converted-fallback.stl");
        try {
            long fallbackTriangles = convert3mfArchiveToAsciiStl(input3mf.toPath(), fallbackStl);
            if (fallbackTriangles > 0 && hasRenderableGeometry(fallbackStl)) {
                logger.warning("Assimp conversion produced empty geometry. Fallback 3MF XML extractor generated "
                        + fallbackTriangles + " triangles.");
                return List.of(fallbackStl.toString());
            }
            logger.warning("3MF XML fallback completed but produced no renderable triangles.");
        } catch (IOException e) {
            logger.warning("3MF XML fallback conversion failed: " + e.getMessage());
        }

        throw new ModelProcessingException(
                "MODEL_CONVERSION_FAILED",
                "Unable to process this 3MF file. Try another format or contact us directly via Request Consultation."
        );
    }

    private long convert3mfToStlWithLwjglAssimp(Path input3mf, Path outputStl) throws IOException {
        int flags = aiProcess_Triangulate
                | aiProcess_JoinIdenticalVertices
                | aiProcess_PreTransformVertices
                | aiProcess_SortByPType;
        AIScene scene = aiImportFile(input3mf.toString(), flags);
        if (scene == null) {
            throw new IOException("LWJGL Assimp import failed: " + aiGetErrorString());
        }

        long triangleCount = 0L;
        try (BufferedWriter writer = Files.newBufferedWriter(outputStl, StandardCharsets.UTF_8)) {
            writer.write("solid converted\n");

            int meshCount = scene.mNumMeshes();
            PointerBuffer meshPointers = scene.mMeshes();
            if (meshCount <= 0 || meshPointers == null) {
                throw new IOException("LWJGL Assimp import contains no meshes");
            }

            for (int meshIndex = 0; meshIndex < meshCount; meshIndex++) {
                long meshPtr = meshPointers.get(meshIndex);
                if (meshPtr == 0L) {
                    continue;
                }
                AIMesh mesh = AIMesh.create(meshPtr);
                AIVector3D.Buffer vertices = mesh.mVertices();
                AIFace.Buffer faces = mesh.mFaces();
                if (vertices == null || faces == null) {
                    continue;
                }

                int vertexCount = mesh.mNumVertices();
                int faceCount = mesh.mNumFaces();
                for (int faceIndex = 0; faceIndex < faceCount; faceIndex++) {
                    AIFace face = faces.get(faceIndex);
                    if (face.mNumIndices() != 3) {
                        continue;
                    }
                    IntBuffer indices = face.mIndices();
                    if (indices == null || indices.remaining() < 3) {
                        continue;
                    }
                    int i0 = indices.get(0);
                    int i1 = indices.get(1);
                    int i2 = indices.get(2);
                    if (i0 < 0 || i1 < 0 || i2 < 0
                            || i0 >= vertexCount
                            || i1 >= vertexCount
                            || i2 >= vertexCount) {
                        continue;
                    }

                    Vec3 p1 = toVec3(vertices.get(i0));
                    Vec3 p2 = toVec3(vertices.get(i1));
                    Vec3 p3 = toVec3(vertices.get(i2));
                    writeAsciiFacet(writer, p1, p2, p3);
                    triangleCount++;
                }
            }

            writer.write("endsolid converted\n");
        } finally {
            aiReleaseImport(scene);
        }

        if (triangleCount <= 0) {
            throw new IOException("LWJGL Assimp conversion produced no triangles");
        }
        return triangleCount;
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

        List<Vec3> vertices = new ArrayList<>();
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

    private Vec3 toVec3(AIVector3D v) {
        return new Vec3(v.x(), v.y(), v.z());
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
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try {
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignored) {
        }
        try {
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        try (InputStream is = zipFile.getInputStream(entry)) {
            return dbf.newDocumentBuilder().parse(is);
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
        List<Element> result = new ArrayList<>();
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
        double[] values = new double[12];
        for (int i = 0; i < 12; i++) {
            try {
                values[i] = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid number in 3MF transform: " + rawTransform, e);
            }
        }
        return new Transform(
                values[0], values[1], values[2],
                values[3], values[4], values[5],
                values[6], values[7], values[8],
                values[9], values[10], values[11]
        );
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

        Vec3 apply(Vec3 vec3) {
            return new Vec3(
                    m00 * vec3.x() + m01 * vec3.y() + m02 * vec3.z() + tx,
                    m10 * vec3.x() + m11 * vec3.y() + m12 * vec3.z() + ty,
                    m20 * vec3.x() + m21 * vec3.y() + m22 * vec3.z() + tz
            );
        }
    }
}
