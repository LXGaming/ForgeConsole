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
import io.github.lxgaming.forgeconsole.task.AddAnsiTask;
import io.github.lxgaming.forgeconsole.task.RemoveAnsiTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.terminal.impl.jansi.JansiSupportImpl;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ForgeConsoleTransformationService implements ITransformationService {
    
    public static final String NAME = "forgeconsole";
    public static final Logger LOGGER = LogManager.getLogger("ForgeConsole Launch");
    
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
        try {
            if (shouldDisableAnsi()) {
                if (RemoveAnsiTask.prepare()) {
                    RemoveAnsiTask.execute();
                }
                
                return;
            }
        } catch (Exception ex) {
            LOGGER.error("Encountered an error", ex);
        }
        
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
            if (AddAnsiTask.prepare()) {
                AddAnsiTask.execute();
            }
        } catch (Exception ex) {
            LOGGER.error("Encountered an error", ex);
        }
    }
    
    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }
    
    private boolean shouldDisableAnsi() throws Exception {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        
        // MultiMC
        return stackTraceElements[stackTraceElements.length - 1].getClassName().equals("org.multimc.EntryPoint");
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