import os
import argparse

def write_structure(root_dir, output_file):
    with open(output_file, 'w', encoding='utf-8') as f:
        for dirpath, dirnames, filenames in os.walk(root_dir):
            relative = os.path.relpath(dirpath, root_dir)
            indent_level = 0 if relative == '.' else relative.count(os.sep) + 1
            indent = '    ' * (indent_level - 1) if indent_level > 0 else ''
            dir_name = os.path.basename(dirpath)
            f.write(f"{indent}{dir_name}/\n")
            for file in sorted(filenames):
                if file.endswith('.java'):
                    f.write(f"{indent}    {file}\n")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate a text file listing .java files with folder structure')
    parser.add_argument('root_dir', nargs='?', default='.', help='Directory to scan')
    parser.add_argument('-o', '--output', default='java_structure.txt', help='Output text file')
    args = parser.parse_args()
    write_structure(args.root_dir, args.output)
