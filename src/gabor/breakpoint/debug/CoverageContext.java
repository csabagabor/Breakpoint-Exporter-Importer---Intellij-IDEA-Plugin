package gabor.breakpoint.debug;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import gabor.breakpoint.debug.type.ComplexType;
import gabor.breakpoint.debug.type.PlainType;
import gabor.breakpoint.debug.type.extract.CallStack;
import gabor.breakpoint.debug.type.var.HistoryVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class CoverageContext {
    public static final String IS_COLLECTION_STRING = "$2!size$%#";
    public static final int IS_COLLECTION_STRING_SIZE = IS_COLLECTION_STRING.length();
    private Project myProject;

    public CoverageContext(Project myProject) {
        this.myProject = myProject;
    }

    public static CoverageContext getInstance(Project project) {
        return ServiceManager.getService(project, CoverageContext.class);
    }

    private Map<VirtualFile, Map<Integer, Set<LineVarInfo>>> varCache;

    public void calcVarCache(VirtualFile virtualFile, List<HistoryVar> variables, int maxOffset, int minOffset, PsiClass containingClass, String methodName) {
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

        if (psiFile == null) {
            return;
        }

        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);

        if (document == null) {
            return;
        }

        PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiFile, maxOffset);
        if (method == null || !method.getName().equals(methodName)) {
            return;
        }

        if (varCache == null) {
            varCache = new HashMap<>();
        }

        Map<String, String> currentFrameVars = new HashMap<>();
        for (HistoryVar variable : variables) {
            String name = variable.getName();
            Object value = variable.getValue();
            if (value instanceof PlainType) {
                currentFrameVars.put(name, ((PlainType) value).getName());
            } else if (value instanceof ComplexType) {
                String typeName = ((ComplexType) value).getName();
                if (typeName.startsWith("java.lang.Boolean") || typeName.startsWith("java.lang.Integer") || typeName.startsWith("java.lang.Long")
                        || typeName.startsWith("java.lang.Float") || typeName.startsWith("java.lang.Double")
                        || typeName.startsWith("java.lang.Short") || typeName.startsWith("java.lang.Character")
                        || typeName.startsWith("java.lang.Byte")) {
                    List<HistoryVar> fieldVariables = variable.getFieldVariables();
                    if (fieldVariables != null && !fieldVariables.isEmpty()) {
                        Object plainType = fieldVariables.get(0).getValue();
                        if (plainType instanceof PlainType) {
                            currentFrameVars.put(name, ((PlainType) plainType).getName());
                        }
                    }
                } else if (variable.getSize() >= 0) {
                    currentFrameVars.put(name, IS_COLLECTION_STRING + " " + variable.getSize());
                }
            } else if (value instanceof String) {
                currentFrameVars.put(name, (String) value);
            }
        }

        Map<Integer, Set<LineVarInfo>> varSet = new HashMap<>();

        method.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiVariable && !(element instanceof PsiField)) {
                    int textOffset = element.getTextOffset();
                    if (textOffset < maxOffset && textOffset >= minOffset) {//else can become slow
                        String name = ((PsiVariable) element).getName();
                        String value = currentFrameVars.get(name);
                        if (value != null) {
                            int lineNumber = document.getLineNumber(textOffset);
                            varSet.computeIfAbsent(lineNumber, k -> new HashSet<>()).add(new LineVarInfo(name, value));
                        }
                    }
                }
                super.visitElement(element);
            }
        });

        varSet.forEach((k, v) -> {
            List<LineVarInfo> collectionInstances = v.stream().filter(t -> t.value.startsWith(IS_COLLECTION_STRING)).collect(Collectors.toList());
            for (LineVarInfo collectionInstance : collectionInstances) {
                collectionInstance.name += ": size";
                collectionInstance.value = collectionInstance.value.substring(CoverageContext.IS_COLLECTION_STRING_SIZE);
            }
        });

        varCache.put(virtualFile, varSet);
    }

    public static class LineVarInfo {
        public LineVarInfo(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String name;
        public String value;
    }

    @Nullable
    public Set<LineVarInfo> getVarsForVirtualFile(VirtualFile virtualFile, int line) {
        if (varCache == null) {
            return null;
        }

        Map<Integer, Set<LineVarInfo>> lineMapping = varCache.get(virtualFile);
        if (lineMapping == null) {
            return null;
        }

        return lineMapping.get(line);
    }

    public void resetVarCache() {
        varCache = null;
    }

    public boolean isVarCacheCalculated() {
        return varCache != null;
    }

}
