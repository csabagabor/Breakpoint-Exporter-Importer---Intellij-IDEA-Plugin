package gabor.breakpoint.debug.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import gabor.breakpoint.helper.LoggingHelper;
import gabor.breakpoint.helper.PluginHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

public abstract class RecordingAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        if (project == null) {
            return;
        }

        try {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, false) {
                public Icon getIcon(VirtualFile file) {
                    return PluginHelper.RECORDING_FILE_EXTENSION.equals(file.getExtension()) ? dressIcon(file, AllIcons.Actions.StartDebugger) : super.getIcon(file);
                }
            };

            VirtualFile recordingDir = FileChooser.chooseFile(descriptor, project, null);
            if (recordingDir != null) {
                openFile(project, new File(recordingDir.getPath()), e);
            }
        } catch (Throwable error) {
            LoggingHelper.error(error);
        }
    }

    protected abstract void openFile(Project project, @NotNull File file, @NotNull AnActionEvent e);

}
