/*
 * Copyright 2020 Alex Thomson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lxgaming.forgeconsole.task;

import io.github.lxgaming.forgeconsole.launch.ForgeConsoleTransformationService;
import io.github.lxgaming.forgeconsole.util.Toolbox;
import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.jline.terminal.impl.jansi.JansiSupportImpl;

import java.lang.reflect.Method;

public class AddAnsiTask {
    
    public static boolean prepare() {
        return true;
    }
    
    public static void execute() throws Exception {
        // org.jline.terminal.impl.jansi.JansiSupportImpl.JANSI_MAJOR_VERSION
        Toolbox.setField(JansiSupportImpl.class.getDeclaredField("JANSI_MAJOR_VERSION"), null, 1);
        // org.jline.terminal.impl.jansi.JansiSupportImpl.JANSI_MINOR_VERSION
        Toolbox.setField(JansiSupportImpl.class.getDeclaredField("JANSI_MINOR_VERSION"), null, 18);
        
        ForgeConsoleTransformationService.LOGGER.debug("Jansi Version: {}.{}", JansiSupportImpl.getJansiMajorVersion(), JansiSupportImpl.getJansiMinorVersion());
        
        // net.minecrell.terminalconsole.TerminalConsoleAppender.initializeTerminal
        Method initializeTerminalMethod = TerminalConsoleAppender.class.getDeclaredMethod("initializeTerminal");
        initializeTerminalMethod.setAccessible(true);
        
        // Reinitialize TerminalConsoleAppender
        TerminalConsoleAppender.close();
        initializeTerminalMethod.invoke(null);
        
        // Move the cursor to the start of the line to overwrite the ANSI reset.
        System.out.print("\u001b[10D");
    }
}