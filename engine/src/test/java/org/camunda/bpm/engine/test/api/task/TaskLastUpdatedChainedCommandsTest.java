/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Deployment(resources = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml")
public class TaskLastUpdatedChainedCommandsTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  TaskService taskService;
  RuntimeService runtimeService;

  @Before
  public void setUp() {
    taskService = engineRule.getTaskService();
    runtimeService = engineRule.getRuntimeService();
  }

  @Test
  public void shouldSetLastUpdatedAfterCmdChain() {
    // given
    runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().singleResult();
    ClockUtil.setCurrentTime(new Date(0));

    // when
    engineRule.getProcessEngineConfiguration().getCommandExecutorTxRequired().execute(new OuterCmd(task.getId()));

    // then
    task = taskService.createTaskQuery().singleResult();
    assertThat(task.getLastUpdated()).isEqualTo(new Date(20));
    assertThat(task.getName()).isEqualTo("myTask");
    assertThat(task.getDescription()).isEqualTo("myDescription");
  }

  private class OuterCmd implements Command<TaskEntity> {

    String taskId;

    public OuterCmd(String taskId) {
      this.taskId = taskId;
    }

    @Override
    public TaskEntity execute(CommandContext commandContext) {
      ClockUtil.setCurrentTime(new Date(10));
      TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
      task.setName("myTask");

      task.triggerUpdateEvent();

      return new InnerCmd(taskId).execute(commandContext);
    }
  }

  private class InnerCmd implements Command<TaskEntity> {

    String taskId;

    public InnerCmd(String taskId) {
      this.taskId = taskId;
    }

    @Override
    public TaskEntity execute(CommandContext commandContext) {
      ClockUtil.setCurrentTime(new Date(20));
      TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
      task.setDescription("myDescription");

      task.triggerUpdateEvent();

      return task;
    }

  }

}
