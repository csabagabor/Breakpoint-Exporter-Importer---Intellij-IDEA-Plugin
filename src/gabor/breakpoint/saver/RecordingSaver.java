package gabor.breakpoint.saver;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import gabor.breakpoint.debug.CoverageContext;
import gabor.breakpoint.debug.DebugExtractor;
import gabor.breakpoint.debug.type.ComplexType;
import gabor.breakpoint.debug.type.HistoryType;
import gabor.breakpoint.debug.type.PlainType;
import gabor.breakpoint.debug.type.extract.CallStack;
import gabor.breakpoint.debug.type.extract.ClassDescription;
import gabor.breakpoint.debug.type.extract.MethodDescription;
import gabor.breakpoint.debug.type.var.*;
import gabor.breakpoint.helper.LoggingHelper;
import gabor.breakpoint.helper.PluginHelper;
import gabor.breakpoint.helper.SequenceAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RecordingSaver {
    private int recursiveLimit = 4;
    private int nrCollectionItems = 25;
    private int nrFields = 25;

    public static final String VARIABLES_FILE = "variables.txt";
    public static final String VARIABLES_FILE_SERIALIZATION = "variables_ser.txt";
    public static final int MAX_FILE_SIZE_TOTAL = 500_000_000;//shouldn't save file > 500 mb
    public static final int MAX_FILE_SIZE_FOR_DUPLICATION = 40_000_000;//40 megabyte which will be 4mb when compressed => serialized file takes only 6-8mbs

    public static void loadRecordingFromFile(Project project, @NotNull File file) {
        try {
            ZipFile zipFile = new ZipFile(file.getAbsoluteFile());
            ZipEntry variablesEntry = zipFile.getEntry(VARIABLES_FILE);
            ZipEntry variablesSerializationEntry = zipFile.getEntry(VARIABLES_FILE_SERIALIZATION);

            //first try to read the kyro file, then if it fails, try to read the serialized file if it exists
            boolean isException = false;
            CallStack callStack = null;
            try {
                if (variablesEntry != null) {
                    Kryo kryo = getKryo();
                    Input input = new Input(zipFile.getInputStream(variablesEntry));
                    callStack = kryo.readObjectOrNull(input, CallStack.class);
                    input.close();
                }
            } catch (Throwable e) {
                isException = true;
                LoggingHelper.error(e);
            }

            if (isException || variablesEntry == null) {
                if (variablesSerializationEntry != null) {
                    ObjectInputStream oi = new ObjectInputStream(zipFile.getInputStream(variablesSerializationEntry));
                    callStack = (CallStack) oi.readObject();
                }
            }

            SequenceAdapter.showVariableView(project, callStack);
        } catch (Exception e) {
            LoggingHelper.error(e);
        }
    }

    public void saveRecordingToFile(@Nullable Project project, @NotNull File file, @NotNull XDebugSession session) {
        if (project == null) {
            return;
        }

        if (file.isDirectory()) {
            String fileName = JOptionPane.showInputDialog("Name of the file");
            file = new File(file, fileName + "." + PluginHelper.RECORDING_FILE_EXTENSION);
        }

        try {
            ProcessHandler processHandler = session.getDebugProcess().getProcessHandler();
            DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(project).getDebugProcess(processHandler);
            DebuggerManagerThread managerThread = debugProcess.getManagerThread();



            OptionsDialogWrapper dialogWrapper = new OptionsDialogWrapper(project);
            dialogWrapper.show();
            if (dialogWrapper.isOK()) {
                nrFields = dialogWrapper.getNrFields();
                nrCollectionItems = dialogWrapper.getNrCollectionItems();
                recursiveLimit = dialogWrapper.getRecursiveLimit();
            }

            DebugExtractor extractor = new DebugExtractor(debugProcess, null,
                    recursiveLimit, true,
                    nrCollectionItems, nrFields, file, getKryo());

            managerThread.invokeCommand(extractor);

            Notification notification = NotificationGroup.toolWindowGroup("demo.notifications.toolWindow", ToolWindowId.DEBUG)
                    .createNotification(
                            "Breakpoint saving in Progress", "", "Please wait until another notification appears",
                            NotificationType.WARNING);
            notification.notify(project);
        } catch (Throwable throwable) {
            LoggingHelper.error(throwable);
        }
    }

    private class DialogPanel extends JPanel {
        private final JSpinner jSpinnerRecursiveLimit;
        private final JSpinner jSpinnerNrFields;
        private final JSpinner jSpinnerNrCollectionItems;

        public DialogPanel() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createTitledBorder("Filter"));
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.insets = JBUI.insets(5);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridwidth = 2;
            JLabel jLabel = new JLabel("Max Depth of variable extraction(larger => more space, but more accurate breakpoint info)");
            add(jLabel, gc);

            gc.gridx = 2;
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.CENTER;
            jSpinnerRecursiveLimit = new JSpinner(new SpinnerNumberModel(recursiveLimit, 1, 1000, 1));
            jLabel.setLabelFor(jSpinnerRecursiveLimit);
            add(jSpinnerRecursiveLimit, gc);

            gc.gridx = 0;
            gc.gridy = 2;
            gc.insets = JBUI.insets(5);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridwidth = 2;
            JLabel jLabel2 = new JLabel("Max number of fields to extract from each class (larger => more space)");
            add(jLabel2, gc);

            gc.gridx = 2;
            gc.gridy = 2;
            gc.anchor = GridBagConstraints.CENTER;
            jSpinnerNrFields = new JSpinner(new SpinnerNumberModel(nrFields, 1, 1000, 1));
            jLabel2.setLabelFor(jSpinnerNrFields);
            add(jSpinnerNrFields, gc);


            gc.gridx = 0;
            gc.gridy = 4;
            gc.insets = JBUI.insets(5);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridwidth = 2;
            JLabel jLabel3 = new JLabel("Max number of Collection/Array/Map elements to extract (larger => more space)");
            add(jLabel3, gc);

            gc.gridx = 2;
            gc.gridy = 4;
            gc.anchor = GridBagConstraints.CENTER;
            jSpinnerNrCollectionItems = new JSpinner(new SpinnerNumberModel(nrCollectionItems, 1, 1000, 1));
            jLabel3.setLabelFor(jSpinnerNrCollectionItems);
            add(jSpinnerNrCollectionItems, gc);
        }
    }

    private class OptionsDialogWrapper extends DialogWrapper {
        private final DialogPanel dialogPanel = new DialogPanel();

        public OptionsDialogWrapper(Project project) {
            super(project, false);
            setResizable(false);
            setTitle("Breakpoint Export Settings");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            return dialogPanel;
        }

        @Nullable
        @Override
        protected JComponent createNorthPanel() {
            return dialogPanel;
        }

        public JComponent getPreferredFocusedComponent() {
            return dialogPanel.jSpinnerRecursiveLimit;
        }

        public int getRecursiveLimit() {
            return (Integer) dialogPanel.jSpinnerRecursiveLimit.getValue();
        }

        public int getNrCollectionItems() {
            return (Integer) dialogPanel.jSpinnerNrCollectionItems.getValue();
        }

        public int getNrFields() {
            return (Integer) dialogPanel.jSpinnerNrFields.getValue();
        }
    }


    public static void writeEntry(byte[] bytes, ZipOutputStream out, String name) throws IOException {
        ZipEntry e = new ZipEntry(name);
        out.putNextEntry(e);
        out.write(bytes, 0, bytes.length);
        out.closeEntry();
    }

    @NotNull
    private static Kryo getKryo() {
        Kryo kryo = new Kryo();
        kryo.register(CallStack.class);
        kryo.register(MethodDescription.class);
        kryo.register(HistoryVar.class);
        kryo.register(ClassDescription.class);
        kryo.register(HistoryLocalVariable.class);
        kryo.register(HistoryEntryVariable.class);
        kryo.register(HistoryPrimitiveVariable.class);
        kryo.register(HistoryArrayVariable.class);
        kryo.register(HistoryEnumVariable.class);
        kryo.register(ComplexType.class);
        kryo.register(PlainType.class);
        kryo.register(HistoryType.class);
        return kryo;
    }
}
