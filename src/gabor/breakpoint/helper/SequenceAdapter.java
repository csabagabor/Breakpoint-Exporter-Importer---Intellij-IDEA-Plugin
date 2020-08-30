package gabor.breakpoint.helper;

import com.intellij.openapi.project.Project;
import gabor.breakpoint.debug.type.StackFrame;
import gabor.breakpoint.debug.type.extract.CallStack;
import gabor.breakpoint.debug.type.var.HistoryVar;
import gabor.breakpoint.debug.view.HistoryToolWindowService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SequenceAdapter {

    public static void showVariableView(Project project, @Nullable CallStack callstack) {
        if (callstack == null) {
            return;
        }

        //construct calls from callstack
        List<StackFrame> stackFrames = new ArrayList<>();

        addStackFramesChildren(stackFrames, callstack, 0);
        Collections.reverse(stackFrames);//to align the stack frame items in the correct order

        if (stackFrames.size() > 0) {
            HistoryToolWindowService.getInstance(project).showToolWindow(
                    stackFrames, stackFrames.get(0));
        }
    }

    private static void addStackFramesChildren(@NotNull List<StackFrame> stackFrames, @Nullable CallStack callstack, int index) {
        if (callstack == null) {
            return;
        }

        try {
            stackFrames.add(convertCallStackToStackFrame(callstack));
        } catch (Throwable e) {
            LoggingHelper.error(e);
        }

        List<CallStack> calls = callstack.getCalls();
        if (calls != null && calls.size() == 1) {
            addStackFramesChildren(stackFrames, calls.get(0), ++index);
        }
    }

    @Nullable
    private static StackFrame convertCallStackToStackFrame(@Nullable CallStack callstack) {
        if (callstack == null) {
            return null;
        }

        if (callstack.getMethod() == null && callstack.getCalls() != null &&
                callstack.getCalls().size() > 0) {
            callstack = callstack.getCalls().get(0);
        }

        String className = callstack.getMethod().getClassDescription().getClassName();
        String classShortName = callstack.getMethod().getClassDescription().getClassShortName();
        String methodName = callstack.getMethod().getMethodName();
        int line = callstack.getMethod().getLine();
        return new StackFrame(classShortName, className, methodName, line - 1, getLocalVariables(callstack), callstack.isProjectClass());
    }

    @NotNull
    private static List<HistoryVar> getLocalVariables(@NotNull CallStack callstack) {
        List<HistoryVar> variables = callstack.getVariables();
        if (variables == null) {
            variables = new ArrayList<>();
        }
        return variables;
    }
}
