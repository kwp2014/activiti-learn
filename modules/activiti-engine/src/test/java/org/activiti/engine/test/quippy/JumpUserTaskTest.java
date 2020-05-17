package org.activiti.engine.test.quippy;

import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.ReadOnlyProcessDefinition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.test.PluggableActivitiTestCase;
import org.activiti.engine.task.Task;

import java.util.List;

/**
 * TODO
 * <p>Created on 2020/5/13 22:49</p>
 *
 * @author quippy
 * @version 1.0
 */
public class JumpUserTaskTest extends PluggableActivitiTestCase {

    public void test() throws Exception {

        // Deploy processes
        String deploymentId = repositoryService.createDeployment()
                .addClasspathResource("org/activiti/engine/test/quippy/JumpUserTaskTest.test.bpmn")
                .deploy()
                .getId();

        runtimeService.startProcessInstanceByKey("jumpUserTask");

        Task task = taskService.createTaskQuery().taskCandidateOrAssigned("ut1").list().get(0);
        taskService.complete(task.getId());

        task = taskService.createTaskQuery().taskCandidateOrAssigned("ut21").singleResult();
        taskService.complete(task.getId());
        // Clean
        repositoryService.deleteDeployment(deploymentId, true);
    }

}