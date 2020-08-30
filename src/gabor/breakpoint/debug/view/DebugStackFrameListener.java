package gabor.breakpoint.debug.view;

import org.jetbrains.annotations.NotNull;

public interface DebugStackFrameListener {
    void onChanged(@NotNull StackFrameManager stackFrameManager);
}
