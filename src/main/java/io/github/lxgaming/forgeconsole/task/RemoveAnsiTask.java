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
import net.minecrell.terminalconsole.util.LoggerNamePatternSelector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.HighlightConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class RemoveAnsiTask {
    
    private static Field noAnsiField;
    
    public static boolean prepare() {
        try {
            noAnsiField = HighlightConverter.class.getDeclaredField("noAnsi");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    
    public static void execute() throws Exception {
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        
        Object consoleAppender = rootLogger.getAppenders().get("Console");
        if (!(consoleAppender instanceof TerminalConsoleAppender)) {
            ForgeConsoleTransformationService.LOGGER.error("Not an instance of TerminalConsoleAppender");
            return;
        }
        
        TerminalConsoleAppender terminalConsoleAppender = (TerminalConsoleAppender) consoleAppender;
        if (!(terminalConsoleAppender.getLayout() instanceof PatternLayout)) {
            ForgeConsoleTransformationService.LOGGER.error("Not an instance of PatternLayout");
            return;
        }
        
        PatternLayout patternLayout = (PatternLayout) terminalConsoleAppender.getLayout();
        
        // org.apache.logging.log4j.core.layout.PatternLayout.eventSerializer
        Field eventSerializerField = PatternLayout.class.getDeclaredField("eventSerializer");
        eventSerializerField.setAccessible(true);
        Object eventSerializer = eventSerializerField.get(patternLayout);
        
        Class<?> patternSelectorSerializerClass = Class.forName("org.apache.logging.log4j.core.layout.PatternLayout$PatternSelectorSerializer");
        // org.apache.logging.log4j.core.layout.PatternLayout$PatternSelectorSerializer.patternSelector
        Field patternSelectorField = patternSelectorSerializerClass.getDeclaredField("patternSelector");
        patternSelectorField.setAccessible(true);
        
        Object patternSelector = patternSelectorField.get(eventSerializer);
        if (!(patternSelector instanceof LoggerNamePatternSelector)) {
            ForgeConsoleTransformationService.LOGGER.error("Not an instance of LoggerNamePatternSelector");
            return;
        }
        
        LoggerNamePatternSelector loggerNamePatternSelector = (LoggerNamePatternSelector) patternSelector;
        
        // net.minecrell.terminalconsole.util.LoggerNamePatternSelector.defaultFormatters
        Field defaultFormattersField = LoggerNamePatternSelector.class.getDeclaredField("defaultFormatters");
        defaultFormattersField.setAccessible(true);
        
        process((PatternFormatter[]) defaultFormattersField.get(patternSelector));
        
        // net.minecrell.terminalconsole.util.LoggerNamePatternSelector.formatters
        Field formattersField = LoggerNamePatternSelector.class.getDeclaredField("formatters");
        formattersField.setAccessible(true);
        
        Class<?> loggerNameSelectorClass = Class.forName("net.minecrell.terminalconsole.util.LoggerNamePatternSelector$LoggerNameSelector");
        // net.minecrell.terminalconsole.util.LoggerNamePatternSelector$LoggerNameSelector.get
        Method getMethod = loggerNameSelectorClass.getDeclaredMethod("get");
        getMethod.setAccessible(true);
        
        List<?> formatters = (List<?>) formattersField.get(loggerNamePatternSelector);
        for (Object formatter : formatters) {
            process((PatternFormatter[]) getMethod.invoke(formatter));
        }
    }
    
    private static void process(PatternFormatter[] patternFormatters) throws Exception {
        for (PatternFormatter patternFormatter : patternFormatters) {
            if (patternFormatter.getConverter() instanceof HighlightConverter) {
                Toolbox.setField(noAnsiField, patternFormatter.getConverter(), true);
            }
        }
    }
}