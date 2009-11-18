package org.gradle.api.tasks

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.file.DefaultDirectoryWalker

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.file.copy.FileCopyActionImpl

@RunWith (org.jmock.integration.junit4.JMock)
public class CopyTest extends AbstractTaskTest {
    Copy copyTask;
    DefaultDirectoryWalker walker;
    FileCopyActionImpl action;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        super.setUp()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        walker = context.mock(DefaultDirectoryWalker.class)
        action = context.mock(FileCopyActionImpl.class)
        copyTask = createTask(Copy.class)
        copyTask.copyAction = action
    }

    public AbstractTask getTask() {
        return copyTask;
    }

    @Test public void executesActionOnExecute() {
        context.checking {
            one(action).hasSource(); will(returnValue(true))
            one(action).getDestinationDir(); will(returnValue(new File('dest')))
            one(action).execute()
            one(action).getDidWork()
        }

        copyTask.copy()
    }
    
    @Test public void usesConventionValuesForDestDirWhenNotSpecified() {
        copyTask.conventionMapping.destinationDir = { new File('convention') }

        context.checking {
            exactly(2).of(action).getDestinationDir()
            will(returnValue(null))
            one(action).into(new File('convention'))
            one(action).hasSource(); will(returnValue(true))
        }

        copyTask.configureRootSpec()
    }

    @Test public void doesNotUseConventionValueForDestDirWhenSpecified() {
        copyTask.conventionMapping.destinationDir = { new File('convention') }

        context.checking {
            one(action).getDestinationDir()
            will(returnValue(new File('dest')))
            one(action).hasSource(); will(returnValue(true))
        }

        copyTask.configureRootSpec()
    }
}
