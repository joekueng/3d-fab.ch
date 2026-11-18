package com.printcalculator.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ThreeMfPlateFixtures {
    private ThreeMfPlateFixtures() {
    }

    static String plate(int number, int instance) {
        return "<plate><metadata key=\"plater_id\" value=\"" + number + "\"/>"
                + (instance < 0 ? "" : "<model_instance><metadata key=\"object_id\" value=\"1\"/>"
                                       + "<metadata key=\"instance_id\" value=\"" + instance + "\"/></model_instance>") + "</plate>";
    }

    static Path project(Path path, String plates) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            put(zip, "3D/3dmodel.model", """
                    <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02"
                           xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06">
                      <resources><object id="1" type="model"><components>
                        <component objectid="7" p:path="/3D/Objects/part.model"/>
                      </components></object></resources>
                      <build><item objectid="1"/>
                        <item objectid="1" transform="1 0 0 0 1 0 0 0 1 400 -300 0"/>
                        <item objectid="1" printable="0"/>
                      </build>
                    </model>
                    """);
            put(zip, "3D/Objects/part.model", """
                    <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
                      <resources><object id="7" type="model"><mesh>
                        <vertices><vertex x="0" y="0" z="0"/><vertex x="20" y="0" z="0"/>
                          <vertex x="0" y="10" z="0"/><vertex x="0" y="0" z="5"/></vertices>
                        <triangles><triangle v1="0" v2="2" v3="1"/><triangle v1="0" v2="1" v3="3"/>
                          <triangle v1="1" v2="2" v3="3"/><triangle v1="2" v2="0" v3="3"/></triangles>
                      </mesh></object></resources><build/>
                    </model>
                    """);
            if (plates != null) put(zip, "Metadata/model_settings.config", "<config>" + plates + "</config>");
            // Deliberately invalid imported printer setting: plate slicing must use the selected profiles.
            put(zip, "Metadata/project_settings.config", "{\"raft_first_layer_expansion\":\"-1\"}");
        }
        return path;
    }

    private static void put(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
