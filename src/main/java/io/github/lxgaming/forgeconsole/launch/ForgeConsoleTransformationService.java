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

package io.github.lxgaming.forgeconsole.launch;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import io.github.lxgaming.classloader.ClassLoaderUtils;
import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.terminal.impl.jansi.JansiSupportImpl;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ForgeConsoleTransformationService implements ITransformationService {
    
    public static final String NAME = "forgeconsole";
    
    private static final Logger LOGGER = LogManager.getLogger("ForgeConsole Launch");
    
    public ForgeConsoleTransformationService() {
        if (Launcher.INSTANCE == null) {
            throw new IllegalStateException("Launcher has not been initialized!");
        }
    }
    
    @Nonnull
    @Override
    public String name() {
        return ForgeConsoleTransformationService.NAME;
    }
    
    @Override
    public void initialize(IEnvironment environment) {
        ensureTransformerExclusion();
    }
    
    @Override
    public void beginScanning(IEnvironment environment) {
    }
    
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        // Is Forge shipping Jansi?
        if (JansiSupportImpl.getJansiMajorVersion() != 0) {
            // Yes, must be the end of the fucken world!
            //                                ____
            //                       __,-~~/~    `---.
            //                     _/_,---(      ,    )
            //                 __ /        <    /   )  \___
            //                ====------------------===;;;==
            //                    \/  ~"~"~"~"~"~\~"~)~",1/
            //                    (_ (   \  (     >    \)
            //                     \_( _ <         >_>'
            //                        ~ `-i' ::>|--"
            //                            I;|.|.|
            //                           <|i::|i|>
            //                            |[::|.|
            //                             ||: |
            // _________________________GROUND ZERO___________________
            return;
        }
        
        // Nope, (╯°□°）╯︵ ┻━┻
        
        try {
            ClassLoaderUtils.appendToClassPath(Launcher.class.getClassLoader(), getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL());
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while attempting to append to the class path", ex);
            throw new IncompatibleEnvironmentException("Failed to append to the class path");
        }
        
        try {
            setField(JansiSupportImpl.class.getDeclaredField("JANSI_MAJOR_VERSION"), null, 1);
            setField(JansiSupportImpl.class.getDeclaredField("JANSI_MINOR_VERSION"), null, 18);
            
            LOGGER.debug("Jansi Version: {}.{}", JansiSupportImpl.getJansiMajorVersion(), JansiSupportImpl.getJansiMinorVersion());
            
            Method initializeTerminalMethod = TerminalConsoleAppender.class.getDeclaredMethod("initializeTerminal");
            initializeTerminalMethod.setAccessible(true);
            
            // Reinitialize TerminalConsoleAppender
            TerminalConsoleAppender.close();
            initializeTerminalMethod.invoke(null);
            
            // Move the cursor to the start of the line to overwrite the ANSI reset.
            System.out.print("\u001b[10D");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }
    
    /**
     * Fixes https://github.com/MinecraftForge/MinecraftForge/pull/6600
     */
    private void ensureTransformerExclusion() {
        try {
            Path path = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                    .map(parentPath -> parentPath.resolve("mods"))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .map(parentPath -> {
                        // Extract the file name
                        String file = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
                        return parentPath.resolve(file.substring(file.lastIndexOf('/') + 1));
                    })
                    .filter(Files::exists)
                    .orElse(null);
            if (path == null) {
                return;
            }
            
            // Check if the path is behind a symbolic link
            if (path.equals(path.toRealPath())) {
                return;
            }
            
            List<Path> transformers = getTransformers();
            if (transformers != null && !transformers.contains(path)) {
                transformers.add(path);
            }
        } catch (Throwable ex) {
            // no-op
        }
    }
    
    private void setField(Field field, Object instance, Object value) {
        try {
            field.setAccessible(true);
            
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            
            field.set(instance, value);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while setting {}.{}", field.getDeclaringClass().getName(), field.getName(), ex);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Path> getTransformers() {
        try {
            Class<?> modDirTransformerDiscovererClass = Class.forName("net.minecraftforge.fml.loading.ModDirTransformerDiscoverer", true, Launcher.class.getClassLoader());
            
            // net.minecraftforge.fml.loading.ModDirTransformerDiscoverer.transformers
            Field transformersField = modDirTransformerDiscovererClass.getDeclaredField("transformers");
            transformersField.setAccessible(true);
            return (List<Path>) transformersField.get(null);
        } catch (Exception ex) {
            LOGGER.error("Encountered an error while getting Transformers", ex);
            return null;
        }
    }
}