/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.build.docs

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.api.tasks.*
import org.gradle.api.logging.LogLevel
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

@CacheableTask
class Docbook2Xhtml extends SourceTask {
    @Classpath
    FileCollection classpath

    @OutputFile @Optional
    File destFile

    @OutputDirectory @Optional
    File destDir

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    File stylesheetsDir

    @Internal
    String stylesheetName

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File getStylesheetFile() {
        new File(stylesheetsDir, stylesheetName)
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    @InputFiles
    FileCollection resources

    @Inject
    WorkerExecutor getWorkerExecuter() {
        throw new UnsupportedOperationException()
    }

    void setWorkerExecuter() {
        throw new UnsupportedOperationException()
    }

    static class Transform implements Runnable {
        private String[] args

        Transform(String stylesheetFile, String fileToTransform, String result, File destDir) {
            this.args = [stylesheetFile, fileToTransform, result, destDir?.absolutePath ?: ""]
        }

        @Override
        void run() {
            XslTransformer.main(args)
        }
    }

    @TaskAction
    def transform() {
        if (!((destFile != null) ^ (destDir != null))) {
            throw new InvalidUserDataException("Must specify exactly 1 of output file or dir.")
        }

        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.INFO)

        source.visit { FileVisitDetails fvd ->
            if (fvd.isDirectory()) {
                return
            }

            File result
            if (destFile) {
                result = destFile
            } else {
                File outFile = fvd.relativePath.replaceLastName(fvd.file.name.replaceAll('.xml$', '.html')).getFile(destDir)
                outFile.parentFile.mkdirs()
                result = outFile
            }

            workerExecuter.submit(Transform) { config ->
                config.displayName = 'Transform ' + fvd.file.name
                config.forkOptions.with {
                    maxHeapSize = '1024m'
                    systemProperty 'xslthl.config', new File("$stylesheetsDir/highlighting/xslthl-config.xml").toURI()
                    systemProperty 'org.apache.xerces.xni.parser.XMLParserConfiguration', 'org.apache.xerces.parsers.XIncludeParserConfiguration'
                }
                config.classpath([ClasspathUtil.getClasspathForClass(XslTransformer)])
                config.classpath(this.classpath)
                config.classpath([new File(stylesheetsDir, 'extensions/xalan27.jar')])
                config.params = [stylesheetFile.absolutePath, fvd.file.absolutePath, result.absolutePath, destDir]
            }
        }

        workerExecuter.await()

        if (resources) {
            project.copy {
                into this.destDir ?: destFile.parentFile
                from resources
            }
        }
    }
}
