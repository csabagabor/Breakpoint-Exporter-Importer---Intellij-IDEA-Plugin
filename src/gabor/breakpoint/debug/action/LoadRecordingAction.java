
package gabor.breakpoint.debug.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import gabor.breakpoint.helper.PluginHelper;
import gabor.breakpoint.saver.RecordingSaver;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LoadRecordingAction extends RecordingAction {

    @Override
    public void openFile(Project project, @NotNull File file, @NotNull AnActionEvent e) {
        if (!file.getAbsolutePath().endsWith(PluginHelper.RECORDING_FILE_EXTENSION)) {
            Messages.showErrorDialog(project, "Select a file which has an extension of ." + PluginHelper.RECORDING_FILE_EXTENSION,
                    "Not a Valid Recording File");
        } else {
            RecordingSaver.loadRecordingFromFile(project, file);
        }
    }
}
