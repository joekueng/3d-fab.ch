package com.printcalculator.tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Deterministic closed meshes, centered on the bed, independent of customer uploads. */
final class CalibrationFixtures {
    private CalibrationFixtures() {}
    record Fixture(String id, String group, Path path) {}

    static List<Fixture> create(Path directory) throws IOException {
        Files.createDirectories(directory);
        var result = new ArrayList<Fixture>();
        box(result, directory, "cube10", "calibration", 10, 10, 10);
        box(result, directory, "cube20", "calibration", 20, 20, 20);
        box(result, directory, "cube50", "calibration", 50, 50, 50);
        box(result, directory, "rod6", "calibration", 6, 6, 40);
        box(result, directory, "tower12", "calibration", 12, 12, 80);
        box(result, directory, "plate100", "calibration", 100, 100, 2);
        box(result, directory, "block60", "calibration", 60, 40, 8);
        cylinder(result, directory, "cylinder16", "calibration", 8, 40);
        box(result, directory, "cube30", "calibration", 30, 30, 30);
        box(result, directory, "rod4", "calibration", 4, 4, 50);
        box(result, directory, "plate80", "calibration", 80, 40, 3);
        tube(result, directory, "tube30", "calibration", 30, 2, 30);
        box(result, directory, "cube25", "validation", 25, 25, 25);
        box(result, directory, "rod5", "validation", 5, 5, 35);
        box(result, directory, "plate60", "validation", 60, 50, 2.5);
        tube(result, directory, "tube24", "validation", 24, 2, 35);
        box(result, directory, "cube80", "calibration", 80, 80, 80);
        box(result, directory, "block120", "calibration", 120, 80, 20);
        tube(result, directory, "tube80", "calibration", 80, 3, 80);
        box(result, directory, "cube65", "validation", 65, 65, 65);
        cylinder(result, directory, "cylinder60", "validation", 30, 70);
        box(result, directory, "plate150", "validation", 150, 100, 5);
        return result;
    }

    private static void box(List<Fixture> result, Path directory, String id, String group,
                            double x, double y, double z) throws IOException {
        double[][] points = {{-x/2,-y/2,0},{x/2,-y/2,0},{x/2,y/2,0},{-x/2,y/2,0},
                {-x/2,-y/2,z},{x/2,-y/2,z},{x/2,y/2,z},{-x/2,y/2,z}};
        int[][] faces = {{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};
        var triangles = new ArrayList<double[][]>();
        for (int[] face : faces) quad(triangles, points[face[0]], points[face[1]], points[face[2]], points[face[3]]);
        write(result, directory, id, group, triangles);
    }

    private static void cylinder(List<Fixture> result, Path directory, String id, String group,
                                 double radius, double height) throws IOException {
        var triangles = new ArrayList<double[][]>();
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            double[] p = {radius*Math.cos(a),radius*Math.sin(a),0};
            double[] q = {radius*Math.cos(b),radius*Math.sin(b),0};
            double[] pt = {p[0],p[1],height}, qt = {q[0],q[1],height};
            quad(triangles,p,q,qt,pt);
            triangles.add(new double[][]{{0,0,0},q,p});
            triangles.add(new double[][]{{0,0,height},pt,qt});
        }
        write(result,directory,id,group,triangles);
    }

    private static void tube(List<Fixture> result, Path directory, String id, String group,
                             double width, double wall, double height) throws IOException {
        double aOuter=width/2,aInner=aOuter-wall;
        double[][] outer = {{-aOuter,-aOuter},{aOuter,-aOuter},{aOuter,aOuter},{-aOuter,aOuter}};
        double[][] inner = {{-aInner,-aInner},{aInner,-aInner},{aInner,aInner},{-aInner,aInner}};
        var triangles = new ArrayList<double[][]>();
        for (int i = 0; i < 4; i++) {
            int j = (i+1)%4;
            var a=outer[i];var b=outer[j];var c=inner[i];var d=inner[j];
            double[] a0={a[0],a[1],0},b0={b[0],b[1],0},c0={c[0],c[1],0},d0={d[0],d[1],0};
            double[] a1={a[0],a[1],height},b1={b[0],b[1],height},c1={c[0],c[1],height},d1={d[0],d[1],height};
            quad(triangles,a0,b0,b1,a1);quad(triangles,c0,c1,d1,d0);
            quad(triangles,a1,b1,d1,c1);quad(triangles,a0,c0,d0,b0);
        }
        write(result,directory,id,group,triangles);
    }

    private static void quad(List<double[][]> triangles, double[] a, double[] b, double[] c, double[] d) {
        triangles.add(new double[][]{a,b,c});triangles.add(new double[][]{a,c,d});
    }

    private static void write(List<Fixture> result, Path directory, String id, String group,
                              List<double[][]> triangles) throws IOException {
        var buffer=ByteBuffer.allocate(84+triangles.size()*50).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(80);buffer.putInt(triangles.size());
        for (double[][] triangle : triangles) {
            buffer.putFloat(0).putFloat(0).putFloat(0);
            for (double[] point : triangle) buffer.putFloat((float)(point[0]+128))
                    .putFloat((float)(point[1]+128)).putFloat((float)point[2]);
            buffer.putShort((short)0);
        }
        Path file=directory.resolve(id+".stl");Files.write(file,buffer.array());
        result.add(new Fixture(id,group,file));
    }
}
