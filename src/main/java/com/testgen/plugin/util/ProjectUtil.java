package com.testgen.plugin.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;

/**
 * ProjectUtil
 *
 * Helper utilities for resolving the current Java file context
 * from action events, editor state, and project navigation.
 */
public class ProjectUtil {

    private static final Logger LOG = Logger.getInstance(ProjectUtil.class);

    /**
     * Resolves the active Java file from an action event.
     * Handles both editor context and project view selection.
     *
     * @param e the action event
     * @return the active PsiJavaFile, or null if no Java file is active
     */
    public static PsiJavaFile getActiveJavaFile(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return null;

        // First try: get from editor (user has the file open)
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile) {
            return (PsiJavaFile) psiFile;
        }

        // Second try: get from virtual file selection (project tree)
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile != null && "java".equals(virtualFile.getExtension())) {
            PsiFile found = PsiManager.getInstance(project).findFile(virtualFile);
            if (found instanceof PsiJavaFile) {
                return (PsiJavaFile) found;
            }
        }

        // Third try: get from current editor's document
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            PsiFile editorFile = com.intellij.psi.PsiDocumentManager
                    .getInstance(project)
                    .getPsiFile(editor.getDocument());
            if (editorFile instanceof PsiJavaFile) {
                return (PsiJavaFile) editorFile;
            }
        }

        return null;
    }

    /**
     * Returns the active VirtualFile from an action event.
     */
    public static VirtualFile getActiveVirtualFile(AnActionEvent e) {
        // From project view selection
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (vf != null) return vf;

        // From editor
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
            if (psiFile != null) {
                return psiFile.getVirtualFile();
            }
        }

        return null;
    }

    /**
     * Returns the current active editor from an action event.
     */
    public static Editor getActiveEditor(AnActionEvent e) {
        return e.getData(CommonDataKeys.EDITOR);
    }

    /**
     * Returns true if the given VirtualFile is a Java file.
     */
    public static boolean isJavaFile(VirtualFile file) {
        return file != null && "java".equalsIgnoreCase(file.getExtension());
    }

    /**
     * Returns true if the given PsiFile is a Java source file
     * (not a test file — we don't generate tests for tests).
     */
    public static boolean isJavaSourceFile(PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return false;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return false;

        // Exclude files already in test source roots
        String path = vf.getPath();
        return !path.contains("/test/") && !path.contains("\\test\\");
    }
}
