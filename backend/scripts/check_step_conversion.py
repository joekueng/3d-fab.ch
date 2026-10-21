"""Exercise STEP conversion using the backend's isolated OpenCascade runtime."""

from pathlib import Path
import hashlib
import math
import os
import re
import struct
import subprocess
import sys
import tempfile
import unittest

from OCP.BRep import BRep_Builder
from OCP.BRepPrimAPI import BRepPrimAPI_MakeBox, BRepPrimAPI_MakeCylinder
from OCP.IFSelect import IFSelect_RetDone
from OCP.STEPControl import STEPControl_Writer, STEPControl_AsIs
from OCP.TopoDS import TopoDS_Compound
from OCP.gp import gp_Pnt

CONVERTER = Path(__file__).resolve().parents[1] / "src/main/resources/model-conversion/step_to_stl.py"


def write_step(shape, path):
    writer = STEPControl_Writer()
    if writer.Transfer(shape, STEPControl_AsIs) != IFSelect_RetDone:
        raise RuntimeError("Could not create STEP fixture")
    if writer.Write(str(path)) != IFSelect_RetDone:
        raise RuntimeError("Could not write STEP fixture")


def stl_properties(data):
    facets = struct.unpack_from("<I", data, 80)[0]
    if not facets or len(data) != 84 + facets * 50:
        raise ValueError("Invalid binary STL")
    points = []
    volume = 0.0
    for triangle in struct.iter_unpack("<12fH", data[84:]):
        a, b, c = triangle[3:6], triangle[6:9], triangle[9:12]
        points.extend((a, b, c))
        volume += (a[0] * (b[1] * c[2] - b[2] * c[1])
                   + a[1] * (b[2] * c[0] - b[0] * c[2])
                   + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6
    sizes = tuple(max(p[axis] for p in points) - min(p[axis] for p in points) for axis in range(3))
    return sizes, abs(volume)


class StepConversionChecks(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix="step_check_")
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)

    def convert(self, source):
        output = source.with_suffix(".stl")
        before = hashlib.sha256(source.read_bytes()).hexdigest()
        result = subprocess.run(
            [sys.executable, "-I", str(CONVERTER), str(source), str(output)],
            capture_output=True, text=True, timeout=120,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), before)
        return stl_properties(output.read_bytes())

    def test_step_and_stp_preserve_box_dimensions_and_volume(self):
        for suffix in (".step", ".stp", ".STP"):
            with self.subTest(suffix=suffix):
                source = self.root / ("box" + suffix)
                write_step(BRepPrimAPI_MakeBox(10, 20, 30).Shape(), source)
                sizes, volume = self.convert(source)
                for actual, expected in zip(sizes, (10, 20, 30)):
                    self.assertAlmostEqual(actual, expected, places=4)
                self.assertAlmostEqual(volume, 6000, places=3)

    def test_multiple_bodies_keep_their_placements(self):
        builder = BRep_Builder()
        compound = TopoDS_Compound()
        builder.MakeCompound(compound)
        builder.Add(compound, BRepPrimAPI_MakeBox(10, 20, 30).Shape())
        builder.Add(compound, BRepPrimAPI_MakeBox(gp_Pnt(40, 50, 60), 10, 20, 30).Shape())
        source = self.root / "assembly.step"
        write_step(compound, source)
        sizes, volume = self.convert(source)
        for actual, expected in zip(sizes, (50, 70, 90)):
            self.assertAlmostEqual(actual, expected, places=4)
        self.assertAlmostEqual(volume, 12000, places=3)

    def test_declared_metres_are_normalized_to_millimetres(self):
        source = self.root / "metres.step"
        write_step(BRepPrimAPI_MakeBox(1, 2, 3).Shape(), source)
        text = source.read_text()
        self.assertIn("SI_UNIT(.MILLI.,.METRE.)", text)
        source.write_text(text.replace("SI_UNIT(.MILLI.,.METRE.)", "SI_UNIT($,.METRE.)"))
        sizes, _ = self.convert(source)
        for actual, expected in zip(sizes, (1000, 2000, 3000)):
            self.assertAlmostEqual(actual, expected, places=2)

    def test_curved_surfaces_are_meshed_accurately(self):
        source = self.root / "cylinder.step"
        write_step(BRepPrimAPI_MakeCylinder(10, 20).Shape(), source)
        sizes, volume = self.convert(source)
        for size in sizes:
            self.assertLess(abs(size - 20), 0.1)
        self.assertLess(abs(volume / (math.pi * 10**2 * 20) - 1), 0.005)

    def test_invalid_and_empty_geometry_fail_without_an_stl(self):
        for name, contents in (("invalid.step", "invalid CAD"), ("empty.stp", "")):
            with self.subTest(name=name):
                source = self.root / name
                source.write_text(contents)
                output = source.with_suffix(".stl")
                result = subprocess.run(
                    [sys.executable, "-I", str(CONVERTER), str(source), str(output)],
                    capture_output=True, text=True, timeout=120,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(output.exists())
                self.assertEqual(source.read_text(), contents)

    @unittest.skipUnless(Path(os.environ.get("SLICER_PATH", "/opt/orcaslicer/AppRun")).is_file(), "OrcaSlicer is not installed")
    def test_slicer_reads_the_generated_stl(self):
        source = self.root / "printable.step"
        write_step(BRepPrimAPI_MakeBox(10, 20, 30).Shape(), source)
        self.convert(source)
        result = subprocess.run(
            [os.environ.get("SLICER_PATH", "/opt/orcaslicer/AppRun"), "--info", str(source.with_suffix(".stl"))],
            cwd=self.root, capture_output=True, text=True, timeout=120,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for axis, expected in zip("xyz", (10, 20, 30)):
            actual = re.search(rf"(?m)^size_{axis}\s*=\s*([0-9.]+)", result.stdout)
            self.assertIsNotNone(actual, result.stdout)
            self.assertAlmostEqual(float(actual.group(1)), expected, places=4)


if __name__ == "__main__":
    unittest.main(verbosity=2)
