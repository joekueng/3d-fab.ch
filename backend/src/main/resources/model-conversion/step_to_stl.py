"""Convert STEP geometry to a binary STL using the isolated OpenCascade runtime."""

from pathlib import Path
import sys

from OCP.BRepMesh import BRepMesh_IncrementalMesh
from OCP.IFSelect import IFSelect_RetDone
from OCP.STEPControl import STEPControl_Reader
from OCP.StlAPI import StlAPI_Writer


def convert(input_path: str, output_path: str) -> None:
    source = Path(input_path)
    output = Path(output_path)
    if source.suffix.lower() not in (".step", ".stp"):
        raise ValueError("Only STEP/STP input is supported")
    if output.suffix.lower() != ".stl" or source.resolve() == output.resolve():
        raise ValueError("A separate STL output file is required")

    reader = STEPControl_Reader()
    if reader.ReadFile(str(source)) != IFSelect_RetDone:
        raise ValueError("The STEP file could not be read")
    # Normalize the units declared in the STEP file to millimetres.
    reader.SetSystemLengthUnit(1.0)
    expected_roots = reader.NbRootsForTransfer()
    if expected_roots == 0 or reader.TransferRoots() != expected_roots:
        raise ValueError("The STEP file could not be imported completely")
    shape = reader.OneShape()
    if shape.IsNull():
        raise ValueError("The STEP file contains no geometry")

    mesher = BRepMesh_IncrementalMesh(shape, 0.05, False, 0.1, False)
    if not mesher.IsDone():
        raise ValueError("The STEP surfaces could not be triangulated")
    writer = StlAPI_Writer()
    writer.ASCIIMode = False
    if not writer.Write(shape, str(output)):
        raise ValueError("The STEP file produced no triangles")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("Expected input file and output file")
        convert(*sys.argv[1:])
    except Exception as error:
        print(f"STEP conversion failed: {error}", file=sys.stderr)
        sys.exit(1)
