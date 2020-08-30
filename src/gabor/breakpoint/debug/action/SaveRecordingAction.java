
package gabor.breakpoint.debug.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import gabor.breakpoint.helper.PluginHelper;
import gabor.breakpoint.saver.RecordingSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;

public class SaveRecordingAction extends RecordingAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        final XDebugSession session = getCurrentSession(e);
        final Presentation presentation = e.getPresentation();

        presentation.setVisible(session != null);
        presentation.setEnabled(session != null && XDebuggerSuspendedActionHandler.isEnabled(session));
    }

    @Override
    public void openFile(Project project, @NotNull File file, @NotNull AnActionEvent e) {
        XDebugSession currentSession = getCurrentSession(e);

        if (currentSession == null) {
            return;
        }

        if (file.getAbsolutePath().endsWith(PluginHelper.RECORDING_FILE_EXTENSION)) {
            int dialogResult = JOptionPane.showConfirmDialog(null, "Would you like to override it?", "Warning", JOptionPane.YES_NO_OPTION);
            if (dialogResult == JOptionPane.YES_OPTION) {
                new RecordingSaver().saveRecordingToFile(project, file, currentSession);
            }
        } else if (!file.isDirectory()) {
            Messages.showErrorDialog(project, "Please select a folder!",
                    "Not a Directory");
        } else {
            new RecordingSaver().saveRecordingToFile(project, file, currentSession);
        }
    }

    @Nullable
    private static XDebugSession getCurrentSession(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        return project == null ? null : XDebuggerManager.getInstance(project).getCurrentSession();
    }
}
