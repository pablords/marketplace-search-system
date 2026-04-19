import os
import re

def add_with_span(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    if '@WithSpan' in content:
        return

    # Add import
    import_line = "import io.opentelemetry.instrumentation.annotations.WithSpan;"
    if import_line not in content:
        content = re.sub(r'package\s+[\w\.]+;', r'\g<0>\n\n' + import_line, content)

    # Add @WithSpan to public methods in classes (simplified)
    # We look for public methods that return something or void
    # Pattern: public <T> name(...) {
    
    def add_annotation(match):
        method_decl = match.group(0)
        if '@Override' in method_decl:
             return "\n    @WithSpan\n    " + method_decl.strip()
        return "\n    @WithSpan\n    " + method_decl.strip()

    # Match public methods, avoiding constructors (which have no return type)
    # This is a bit naive but should work for most UseCases/Services
    content = re.sub(r'(public\s+[\w<>[\]]+\s+\w+\s*\([^)]*\)\s*(?:throws\s+[\w\s,]+)?\s*\{)', r'@WithSpan\n    \1', content)
    
    with open(file_path, 'w') as f:
        f.write(content)

# Target directories
targets = [
    "catalog-service/application/src/main/java/com/marketplace/search/catalog/application/usecases",
    "catalog-service/domain/src/main/java/com/marketplace/search/catalog/domain/services",
    "search-service/application/src/main/java/com/marketplace/search/search/application/usecases",
    "search-service/domain/src/main/java/com/marketplace/search/search/domain/services",
    "indexing-service/application/src/main/java/com/marketplace/search/indexing/application/usecases",
    "indexing-service/domain/src/main/java/com/marketplace/search/indexing/domain/services"
]

base_path = "/home/pablo/projetos/marketplace-search-system"

for target in targets:
    dir_path = os.path.join(base_path, target)
    if not os.path.exists(dir_path):
        continue
    for root, dirs, files in os.walk(dir_path):
        for file in files:
            if file.endswith(".java"):
                add_with_span(os.path.join(root, file))
