/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.flowable.engine.test.bpmn.callactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.DefaultTenantProvider;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.util.CollectionUtil;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.DeleteReason;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.test.HistoryTestHelper;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.entitylink.api.EntityLink;
import org.flowable.entitylink.api.EntityLinkType;
import org.flowable.entitylink.api.HierarchyType;
import org.flowable.entitylink.api.history.HistoricEntityLink;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.Test;

/**
 * @author Joram Barrez
 * @author Nils Preusker
 * @author Bernd Ruecker
 * @author Joram Barrez
 */
public class CallActivityAdvancedTest extends PluggableFlowableTestCase {

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml", 
                    "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testCallSimpleSubProcess() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSimpleSubProcess");

        // one task in the subprocess should be active after starting the process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
        
        Task childTask = taskService.createTaskQuery().processInstanceIdWithChildren(processInstance.getId()).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskBeforeSubProcess.getId());

        // Completing the task continues the process which leads to calling the subprocess
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
        Execution execution = runtimeService.createExecutionQuery().executionId(taskInSubProcess.getExecutionId()).singleResult();
        assertThat(execution.getRootProcessInstanceId()).isEqualTo(processInstance.getId());
        assertThat(execution.getProcessInstanceId()).isNotEqualTo(execution.getRootProcessInstanceId());
        managementService.executeCommand(new Command<Void>() {

            @Override
            public Void execute(CommandContext commandContext) {
                ExecutionEntity rootProcessInstance = ((ExecutionEntity) execution).getRootProcessInstance();
                assertThat(rootProcessInstance).isNotNull();
                assertThat(rootProcessInstance.getId()).isEqualTo(processInstance.getId());
                return null;
            }
            
        });
        
        List<EntityLink> entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(processInstance.getId());
        assertThat(entityLinks).hasSize(3);
        EntityLink entityLinkSubProcess = null;
        EntityLink entityLinkTask = null;
        EntityLink entityLinkSubTask = null;
        for (EntityLink entityLink : entityLinks) {
            if (ScopeTypes.TASK.equals(entityLink.getReferenceScopeType())) {
                if (taskBeforeSubProcess.getId().equals(entityLink.getReferenceScopeId())) {
                    entityLinkTask = entityLink;
                } else {
                    entityLinkSubTask = entityLink;
                }
                
            } else if (ScopeTypes.BPMN.equals(entityLink.getReferenceScopeType())) {
                entityLinkSubProcess = entityLink;
            }
        }
        assertThat(entityLinkSubProcess).isNotNull();
        assertThat(entityLinkTask).isNotNull();
        assertThat(entityLinkSubTask).isNotNull();
        
        assertThat(entityLinkSubProcess.getScopeId()).isEqualTo(processInstance.getId());
        assertThat(entityLinkSubProcess.getScopeType()).isEqualTo(ScopeTypes.BPMN);
        assertThat(entityLinkSubProcess.getScopeDefinitionId()).isNull();
        assertThat(entityLinkSubProcess.getReferenceScopeId()).isEqualTo(execution.getProcessInstanceId());
        assertThat(entityLinkSubProcess.getReferenceScopeType()).isEqualTo(ScopeTypes.BPMN);
        assertThat(entityLinkSubProcess.getReferenceScopeDefinitionId()).isNull();
        assertThat(entityLinkSubProcess.getLinkType()).isEqualTo(EntityLinkType.CHILD);
        assertThat(entityLinkSubProcess.getCreateTime()).isNotNull();
        assertThat(entityLinkSubProcess.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
        
        assertThat(entityLinkTask.getScopeId()).isEqualTo(processInstance.getId());
        assertThat(entityLinkTask.getScopeType()).isEqualTo(ScopeTypes.BPMN);
        assertThat(entityLinkTask.getScopeDefinitionId()).isNull();
        assertThat(entityLinkTask.getReferenceScopeId()).isEqualTo(taskBeforeSubProcess.getId());
        assertThat(entityLinkTask.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
        assertThat(entityLinkTask.getReferenceScopeDefinitionId()).isNull();
        assertThat(entityLinkTask.getLinkType()).isEqualTo(EntityLinkType.CHILD);
        assertThat(entityLinkTask.getCreateTime()).isNotNull();
        assertThat(entityLinkTask.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
        
        assertThat(entityLinkSubTask.getScopeId()).isEqualTo(processInstance.getId());
        assertThat(entityLinkSubTask.getScopeType()).isEqualTo(ScopeTypes.BPMN);
        assertThat(entityLinkSubTask.getScopeDefinitionId()).isNull();
        assertThat(entityLinkSubTask.getReferenceScopeId()).isEqualTo(taskInSubProcess.getId());
        assertThat(entityLinkSubTask.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
        assertThat(entityLinkSubTask.getReferenceScopeDefinitionId()).isNull();
        assertThat(entityLinkSubTask.getLinkType()).isEqualTo(EntityLinkType.CHILD);
        assertThat(entityLinkSubTask.getCreateTime()).isNotNull();
        assertThat(entityLinkSubTask.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
        
        entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(execution.getProcessInstanceId());
        assertThat(entityLinks).hasSize(1);
        EntityLink entityLink = entityLinks.get(0);
        
        assertThat(entityLink.getScopeId()).isEqualTo(execution.getProcessInstanceId());
        assertThat(entityLink.getScopeType()).isEqualTo(ScopeTypes.BPMN);
        assertThat(entityLink.getScopeDefinitionId()).isNull();
        assertThat(entityLink.getReferenceScopeId()).isEqualTo(taskInSubProcess.getId());
        assertThat(entityLink.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
        assertThat(entityLink.getReferenceScopeDefinitionId()).isNull();
        assertThat(entityLink.getLinkType()).isEqualTo(EntityLinkType.CHILD);
        assertThat(entityLink.getCreateTime()).isNotNull();
        assertThat(entityLink.getHierarchyType()).isEqualTo(HierarchyType.PARENT);
        
        childTask = taskService.createTaskQuery().processInstanceIdWithChildren(processInstance.getId()).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskInSubProcess.getId());
        
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
            List<HistoricTaskInstance> childHistoricTasks = historyService.createHistoricTaskInstanceQuery()
                            .processInstanceIdWithChildren(processInstance.getId())
                            .list();
            assertThat(childHistoricTasks).hasSize(2);
            List<String> taskIds = new ArrayList<>();
            for (HistoricTaskInstance task : childHistoricTasks) {
                taskIds.add(task.getId());
            }
            assertThat(taskIds.contains(taskBeforeSubProcess.getId())).isTrue();
            assertThat(taskIds.contains(taskInSubProcess.getId())).isTrue();
        }
        
        childTask = taskService.createTaskQuery().processInstanceIdWithChildren(execution.getProcessInstanceId()).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskInSubProcess.getId());

        // Completing the task in the subprocess, finishes the subprocess
        taskService.complete(taskInSubProcess.getId());
        Task taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");
        
        childTask = taskService.createTaskQuery().processInstanceIdWithChildren(processInstance.getId()).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskAfterSubProcess.getId());

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());
        assertProcessEnded(processInstance.getId());

        // Validate subprocess history
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
            // Subprocess should have initial activity set
            HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery().processInstanceId(taskInSubProcess.getProcessInstanceId()).singleResult();
            assertThat(historicProcess).isNotNull();
            assertThat(historicProcess.getStartActivityId()).isEqualTo("theStart");

            List<HistoricActivityInstance> subProcesshistoricInstances = historyService.createHistoricActivityInstanceQuery().processInstanceId(taskInSubProcess.getProcessInstanceId()).list();

            // Should contain a start-event, the task and an end-event
            assertThat(subProcesshistoricInstances).hasSize(5);
            Set<String> expectedActivities = new HashSet<>(Arrays.asList(new String[]{"theStart", "flow1", "task", "flow2", "theEnd"}));

            for (HistoricActivityInstance act : subProcesshistoricInstances) {
                expectedActivities.remove(act.getActivityId());
            }
            assertThat(expectedActivities.isEmpty()).as("Not all expected activities were found in the history").isTrue();

            List<HistoricActivityInstance> historicInstances = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstance.getProcessInstanceId()).list();

            assertThat(historicInstances).hasSize(9);
            expectedActivities = new HashSet<>(Arrays.asList(new String[]{"theStart", "flow1", "taskBeforeSubProcess", "flow2", "callSubProcess", "flow3",
                "taskAfterSubProcess", "flow4", "theEnd"
            }));

            for (HistoricActivityInstance act : historicInstances) {
                expectedActivities.remove(act.getActivityId());
            }
            assertThat(expectedActivities.isEmpty()).as("Not all expected activities were found in the history").isTrue();

            if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.AUDIT, processEngineConfiguration)) {
                List<HistoricEntityLink> historicEntityLinks = historyService.getHistoricEntityLinkChildrenForProcessInstance(processInstance.getId());
                assertThat(historicEntityLinks).hasSize(4);
                HistoricEntityLink historicEntityLinkSubProcess = null;
                HistoricEntityLink historicEntityLinkTask = null;
                HistoricEntityLink historicEntityLinkSubTask = null;
                HistoricEntityLink historicEntityLinkAfterTask = null;
                for (HistoricEntityLink historicEntityLink : historicEntityLinks) {
                    if (ScopeTypes.TASK.equals(historicEntityLink.getReferenceScopeType())) {
                        if (taskBeforeSubProcess.getId().equals(historicEntityLink.getReferenceScopeId())) {
                            historicEntityLinkTask = historicEntityLink;
                        } else if (taskAfterSubProcess.getId().equals(historicEntityLink.getReferenceScopeId())) {
                            historicEntityLinkAfterTask = historicEntityLink;
                        } else {
                            historicEntityLinkSubTask = historicEntityLink;
                        }
                        
                    } else if (ScopeTypes.BPMN.equals(historicEntityLink.getReferenceScopeType())) {
                        historicEntityLinkSubProcess = historicEntityLink;
                    }
                }
                assertThat(historicEntityLinkSubProcess).isNotNull();
                assertThat(historicEntityLinkTask).isNotNull();
                assertThat(historicEntityLinkSubTask).isNotNull();
                
                assertThat(historicEntityLinkSubProcess.getScopeId()).isEqualTo(processInstance.getId());
                assertThat(historicEntityLinkSubProcess.getScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLinkSubProcess.getScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkSubProcess.getReferenceScopeId()).isEqualTo(execution.getProcessInstanceId());
                assertThat(historicEntityLinkSubProcess.getReferenceScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLinkSubProcess.getReferenceScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkSubProcess.getLinkType()).isEqualTo(EntityLinkType.CHILD);
                assertThat(historicEntityLinkSubProcess.getCreateTime()).isNotNull();
                assertThat(historicEntityLinkSubProcess.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
                
                assertThat(historicEntityLinkTask.getScopeId()).isEqualTo(processInstance.getId());
                assertThat(historicEntityLinkTask.getScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLinkTask.getScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkTask.getReferenceScopeId()).isEqualTo(taskBeforeSubProcess.getId());
                assertThat(historicEntityLinkTask.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
                assertThat(historicEntityLinkTask.getReferenceScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkTask.getLinkType()).isEqualTo(EntityLinkType.CHILD);
                assertThat(historicEntityLinkTask.getCreateTime()).isNotNull();
                assertThat(historicEntityLinkTask.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
                
                assertThat(historicEntityLinkSubTask.getScopeId()).isEqualTo(processInstance.getId());
                assertThat(historicEntityLinkSubTask.getScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLinkSubTask.getScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkSubTask.getReferenceScopeId()).isEqualTo(taskInSubProcess.getId());
                assertThat(historicEntityLinkSubTask.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
                assertThat(historicEntityLinkSubTask.getReferenceScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkSubTask.getLinkType()).isEqualTo(EntityLinkType.CHILD);
                assertThat(historicEntityLinkSubTask.getCreateTime()).isNotNull();
                assertThat(historicEntityLinkSubTask.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
                
                assertThat(historicEntityLinkAfterTask.getScopeId()).isEqualTo(processInstance.getId());
                assertThat(historicEntityLinkAfterTask.getScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLinkAfterTask.getScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkAfterTask.getReferenceScopeId()).isEqualTo(taskAfterSubProcess.getId());
                assertThat(historicEntityLinkAfterTask.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
                assertThat(historicEntityLinkAfterTask.getReferenceScopeDefinitionId()).isNull();
                assertThat(historicEntityLinkAfterTask.getLinkType()).isEqualTo(EntityLinkType.CHILD);
                assertThat(historicEntityLinkAfterTask.getCreateTime()).isNotNull();
                assertThat(historicEntityLinkAfterTask.getHierarchyType()).isEqualTo(HierarchyType.ROOT);
                
                historicEntityLinks = historyService.getHistoricEntityLinkChildrenForProcessInstance(execution.getProcessInstanceId());
                assertThat(historicEntityLinks).hasSize(1);
                HistoricEntityLink historicEntityLink = historicEntityLinks.get(0);
                
                assertThat(historicEntityLink.getScopeId()).isEqualTo(execution.getProcessInstanceId());
                assertThat(historicEntityLink.getScopeType()).isEqualTo(ScopeTypes.BPMN);
                assertThat(historicEntityLink.getScopeDefinitionId()).isNull();
                assertThat(historicEntityLink.getReferenceScopeId()).isEqualTo(taskInSubProcess.getId());
                assertThat(historicEntityLink.getReferenceScopeType()).isEqualTo(ScopeTypes.TASK);
                assertThat(historicEntityLink.getReferenceScopeDefinitionId()).isNull();
                assertThat(historicEntityLink.getLinkType()).isEqualTo(EntityLinkType.CHILD);
                assertThat(historicEntityLink.getCreateTime()).isNotNull();
                assertThat(historicEntityLink.getHierarchyType()).isEqualTo(HierarchyType.PARENT);
                
                List<HistoricTaskInstance> childHistoricTasks = historyService.createHistoricTaskInstanceQuery()
                                .processInstanceIdWithChildren(processInstance.getId())
                                .list();
                assertThat(childHistoricTasks).hasSize(3);
                List<String> taskIds = new ArrayList<>();
                for (HistoricTaskInstance task : childHistoricTasks) {
                    taskIds.add(task.getId());
                }
                assertThat(taskIds.contains(taskBeforeSubProcess.getId())).isTrue();
                assertThat(taskIds.contains(taskInSubProcess.getId())).isTrue();
                assertThat(taskIds.contains(taskAfterSubProcess.getId())).isTrue();

                HistoricTaskInstance childHistoricTask = historyService.createHistoricTaskInstanceQuery()
                                .processInstanceIdWithChildren(execution.getProcessInstanceId())
                                .singleResult();
                assertThat(childHistoricTask.getId()).isEqualTo(taskInSubProcess.getId());
            }
        }
    }

    @Test
    @Deployment(resources = {
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testThreeLevelSubProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testThreeLevelSubProcess() {
        // The threeLevelSubProcess has the following structure
        // threeLevelSubProcess
        // - taskBeforeSubProcess (UserTask)
        // - callSubProcess (CallActivity)
        //     - taskBeforeSubProcess (UserTask)
        //     - callSubProcess (CallActivity)
        //         - task (UserTask)
        //     - taskAfterSubProcess (UserTask)
        // - taskAfterSubProcess (UserTask)

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("threeLevelSubProcess");

        // one task in the subprocess should be active after starting the process instance
        Task taskBeforeSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");

        String rootInstanceId = processInstance.getId();
        Task childTask = taskService.createTaskQuery().processInstanceIdWithChildren(rootInstanceId).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskBeforeSubProcess.getId());

        // Completing the task continues the process which leads to calling the subprocess
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskBeforeSubProcessInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskBeforeSubProcessInSubProcess.getName()).isEqualTo("Task before subprocess");
        assertThat(taskBeforeSubProcessInSubProcess.getId()).isNotEqualTo(taskBeforeSubProcess.getId());

        // Completing the task continues the process which leads to calling the last subprocess
        taskService.complete(taskBeforeSubProcessInSubProcess.getId());
        Task taskInLastSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskInLastSubProcess.getName()).isEqualTo("Task in subprocess");

        ProcessInstance firstSubProcess = runtimeService.createProcessInstanceQuery().superProcessInstanceId(rootInstanceId).singleResult();
        String firstProcessId = firstSubProcess.getId();
        assertThat(taskBeforeSubProcessInSubProcess.getProcessInstanceId()).isEqualTo(firstProcessId);

        ProcessInstance secondSubProcess = runtimeService.createProcessInstanceQuery().superProcessInstanceId(firstProcessId).singleResult();
        assertThat(taskInLastSubProcess.getProcessInstanceId()).isEqualTo(secondSubProcess.getId());

        List<EntityLink> entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(rootInstanceId);

        assertThat(entityLinks)
                .extracting(EntityLink::getScopeId, EntityLink::getScopeType, EntityLink::getHierarchyType, EntityLink::getReferenceScopeId,
                        EntityLink::getReferenceScopeType, EntityLink::getLinkType)
                .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                .containsExactlyInAnyOrder(
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, firstProcessId, ScopeTypes.BPMN, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, secondSubProcess.getId(), ScopeTypes.BPMN, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        // The sub process tasks even though completed keep their entity links until the process instance is completed
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                );

        assertThat(entityLinks)
                .extracting(EntityLink::getRootScopeId, EntityLink::getRootScopeType)
                .containsOnly(
                        tuple(rootInstanceId, ScopeTypes.BPMN)
                );

        entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(firstSubProcess.getProcessInstanceId());
        assertThat(entityLinks)
                .extracting(EntityLink::getScopeId, EntityLink::getScopeType, EntityLink::getHierarchyType, EntityLink::getReferenceScopeId,
                        EntityLink::getReferenceScopeType, EntityLink::getLinkType)
                .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                .containsExactlyInAnyOrder(
                        tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, secondSubProcess.getId(), ScopeTypes.BPMN, EntityLinkType.CHILD),
                        tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.GRAND_PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        // The sub process tasks even though completed keep their entity links until the sub process is not completed
                        tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, taskBeforeSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                );

        assertThat(entityLinks)
                .extracting(EntityLink::getRootScopeId, EntityLink::getRootScopeType)
                .containsOnly(
                        tuple(rootInstanceId, ScopeTypes.BPMN)
                );

        entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(secondSubProcess.getProcessInstanceId());
        assertThat(entityLinks)
                .extracting(EntityLink::getScopeId, EntityLink::getScopeType, EntityLink::getHierarchyType, EntityLink::getReferenceScopeId,
                        EntityLink::getReferenceScopeType, EntityLink::getLinkType)
                .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                .containsExactlyInAnyOrder(
                        tuple(secondSubProcess.getId(), ScopeTypes.BPMN, HierarchyType.PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                );

        assertThat(entityLinks)
                .extracting(EntityLink::getRootScopeId, EntityLink::getRootScopeType)
                .containsOnly(
                        tuple(rootInstanceId, ScopeTypes.BPMN)
                );

        List<EntityLink> taskEntityLinks = runtimeService.getEntityLinkParentsForTask(taskInLastSubProcess.getId());
        assertThat(taskEntityLinks)
                .extracting(EntityLink::getScopeId, EntityLink::getScopeType, EntityLink::getHierarchyType, EntityLink::getReferenceScopeId,
                        EntityLink::getReferenceScopeType, EntityLink::getLinkType)
                .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                .containsExactlyInAnyOrder(
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.GRAND_PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(secondSubProcess.getId(), ScopeTypes.BPMN, HierarchyType.PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                );

        assertThat(taskEntityLinks)
                .extracting(EntityLink::getRootScopeId, EntityLink::getRootScopeType)
                .containsOnly(
                        tuple(rootInstanceId, ScopeTypes.BPMN)
                );

        childTask = taskService.createTaskQuery().processInstanceIdWithChildren(rootInstanceId).singleResult();
        assertThat(childTask.getId()).isEqualTo(taskInLastSubProcess.getId());

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
            List<HistoricTaskInstance> childHistoricTasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceIdWithChildren(rootInstanceId)
                    .list();
            assertThat(childHistoricTasks)
                    .extracting(HistoricTaskInstance::getId)
                    .containsOnly(taskBeforeSubProcess.getId(), taskBeforeSubProcessInSubProcess.getId(), taskInLastSubProcess.getId());
        }

        // Completing the task in the second subprocess, finishes the second subprocess
        taskService.complete(taskInLastSubProcess.getId());
        Task taskAfterSubProcessInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskAfterSubProcessInSubProcess.getName()).isEqualTo("Task after subprocess");

        // Completing this task finishes the first subproces
        taskService.complete(taskAfterSubProcessInSubProcess.getId());
        Task taskAfterSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

        entityLinks = runtimeService.getEntityLinkChildrenForProcessInstance(rootInstanceId);

        assertThat(entityLinks)
                .extracting(EntityLink::getScopeId, EntityLink::getScopeType, EntityLink::getHierarchyType, EntityLink::getReferenceScopeId,
                        EntityLink::getReferenceScopeType, EntityLink::getLinkType)
                .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                .containsExactlyInAnyOrder(
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, firstProcessId, ScopeTypes.BPMN, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, secondSubProcess.getId(), ScopeTypes.BPMN, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                        tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                );

        assertThat(entityLinks)
                .extracting(EntityLink::getRootScopeId, EntityLink::getRootScopeType)
                .containsOnly(
                        tuple(rootInstanceId, ScopeTypes.BPMN)
                );

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());
        assertProcessEnded(rootInstanceId);

        // Validate subprocess history entity links
        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.AUDIT, processEngineConfiguration)) {

            List<HistoricEntityLink> historicEntityLinks = historyService.getHistoricEntityLinkChildrenForProcessInstance(rootInstanceId);

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, firstProcessId, ScopeTypes.BPMN, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskBeforeSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, secondSubProcess.getId(), ScopeTypes.BPMN, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );

            historicEntityLinks = historyService.getHistoricEntityLinkChildrenForProcessInstance(firstProcessId);

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, taskBeforeSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, secondSubProcess.getId(), ScopeTypes.BPMN, EntityLinkType.CHILD),
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.GRAND_PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, taskAfterSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );

            historicEntityLinks = historyService.getHistoricEntityLinkChildrenForProcessInstance(secondSubProcess.getId());

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(secondSubProcess.getId(), ScopeTypes.BPMN, HierarchyType.PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(historicEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );

            List<HistoricEntityLink> taskHistoricEntityLinks = historyService.getHistoricEntityLinkParentsForTask(taskInLastSubProcess.getId());

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.GRAND_PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(secondSubProcess.getId(), ScopeTypes.BPMN, HierarchyType.PARENT, taskInLastSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );

            taskHistoricEntityLinks = historyService.getHistoricEntityLinkParentsForTask(taskAfterSubProcessInSubProcess.getId());

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD),
                            tuple(firstProcessId, ScopeTypes.BPMN, HierarchyType.PARENT, taskAfterSubProcessInSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );

            taskHistoricEntityLinks = historyService.getHistoricEntityLinkParentsForTask(taskAfterSubProcess.getId());

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getScopeId, HistoricEntityLink::getScopeType, HistoricEntityLink::getHierarchyType,
                            HistoricEntityLink::getReferenceScopeId, HistoricEntityLink::getReferenceScopeType, HistoricEntityLink::getLinkType)
                    .as("scopeId, scopeType, hierarchyType, referenceScopeId, referenceScopeType, linkType")
                    .containsExactlyInAnyOrder(
                            tuple(rootInstanceId, ScopeTypes.BPMN, HierarchyType.ROOT, taskAfterSubProcess.getId(), ScopeTypes.TASK, EntityLinkType.CHILD)
                    );

            assertThat(taskHistoricEntityLinks)
                    .extracting(HistoricEntityLink::getRootScopeId, HistoricEntityLink::getRootScopeType)
                    .containsOnly(
                            tuple(rootInstanceId, ScopeTypes.BPMN)
                    );
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithExpressions.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testCallSimpleSubProcessWithExpressions() {

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSimpleSubProcess");

        // one task in the subprocess should be active after starting the process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");

        // Completing the task continues the process which leads to calling the
        // subprocess. The sub process we want to call is passed in as a variable into this task
        taskService.setVariable(taskBeforeSubProcess.getId(), "simpleSubProcessExpression", "simpleSubProcess");
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        // Completing the task in the subprocess, finishes the subprocess
        taskService.complete(taskInSubProcess.getId());
        Task taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());
        assertProcessEnded(processInstance.getId());
    }

    /**
     * Test case for a possible tricky case: reaching the end event of the subprocess leads to an end event in the super process instance.
     */
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testSubProcessEndsSuperProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testSubProcessEndsSuperProcess() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("subProcessEndsSuperProcess");

        // one task in the subprocess should be active after starting the process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task in subprocess");

        // Completing this task ends the subprocess which leads to the end of the whole process instance
        taskService.complete(taskBeforeSubProcess.getId());
        assertProcessEnded(processInstance.getId());
        assertThat(runtimeService.createExecutionQuery().list()).isEmpty();
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallParallelSubProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleParallelSubProcess.bpmn20.xml" })
    public void testCallParallelSubProcess() {
        runtimeService.startProcessInstanceByKey("callParallelSubProcess");

        // The two tasks in the parallel subprocess should be active
        TaskQuery taskQuery = taskService.createTaskQuery().orderByTaskName().asc();
        List<Task> tasks = taskQuery.list();
        assertThat(tasks).hasSize(2);

        Task taskA = tasks.get(0);
        Task taskB = tasks.get(1);
        assertThat(taskA.getName()).isEqualTo("Task A");
        assertThat(taskB.getName()).isEqualTo("Task B");

        // Completing the first task should not end the subprocess
        taskService.complete(taskA.getId());
        assertThat(taskQuery.list()).hasSize(1);

        // Completing the second task should end the subprocess and end the whole process instance
        taskService.complete(taskB.getId());
        assertThat(runtimeService.createExecutionQuery().count()).isZero();
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSequentialSubProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithExpressions.bpmn20.xml", "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess2.bpmn20.xml" })
    public void testCallSequentialSubProcessWithExpressions() {

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSequentialSubProcess");

        // FIRST sub process calls simpleSubProcess

        // one task in the subprocess should be active after starting the process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");

        // Completing the task continues the process which leads to calling the
        // subprocess. The sub process we want to call is passed in as a variable into this task
        taskService.setVariable(taskBeforeSubProcess.getId(), "simpleSubProcessExpression", "simpleSubProcess");
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        // Completing the task in the subprocess, finishes the subprocess
        taskService.complete(taskInSubProcess.getId());
        Task taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());

        // SECOND sub process calls simpleSubProcess2

        // one task in the subprocess should be active after starting the process instance
        taskQuery = taskService.createTaskQuery();
        taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");

        // Completing the task continues the process which leads to calling the
        // subprocess. The sub process we want to call is passed in as a variable into this task
        taskService.setVariable(taskBeforeSubProcess.getId(), "simpleSubProcessExpression", "simpleSubProcess2");
        taskService.complete(taskBeforeSubProcess.getId());
        taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess 2");

        // Completing the task in the subprocess, finishes the subprocess
        taskService.complete(taskInSubProcess.getId());
        taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());
        assertProcessEnded(processInstance.getId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testTimerOnCallActivity.bpmn20.xml", "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testTimerOnCallActivity() {
        Date startTime = processEngineConfiguration.getClock().getCurrentTime();

        // After process start, the task in the subprocess should be active
        ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("timerOnCallActivity");
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        ProcessInstance pi2 = runtimeService.createProcessInstanceQuery().superProcessInstanceId(pi1.getId()).singleResult();

        // When the timer on the subprocess is fired, the complete subprocess is destroyed
        processEngineConfiguration.getClock().setCurrentTime(new Date(startTime.getTime() + (6 * 60 * 1000))); // + 6 minutes, timer fires on 5 minutes
        waitForJobExecutorToProcessAllJobs(10000, 7000L);

        Task escalatedTask = taskQuery.singleResult();
        assertThat(escalatedTask.getName()).isEqualTo("Escalated Task");

        // Completing the task ends the complete process
        taskService.complete(escalatedTask.getId());
        assertThat(runtimeService.createExecutionQuery().list()).isEmpty();

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.AUDIT, processEngineConfiguration)) {
            assertThat(historyService.createHistoricProcessInstanceQuery().processInstanceId(pi2.getId()).singleResult()
                    .getDeleteReason().startsWith(DeleteReason.BOUNDARY_EVENT_INTERRUPTING)).isTrue();
            assertHistoricTasksDeleteReason(pi2, DeleteReason.BOUNDARY_EVENT_INTERRUPTING, "Task in subprocess");
            assertHistoricActivitiesDeleteReason(pi1, DeleteReason.BOUNDARY_EVENT_INTERRUPTING, "callSubProcess");
            assertHistoricActivitiesDeleteReason(pi2, DeleteReason.BOUNDARY_EVENT_INTERRUPTING, "task");
        }
    }

    /**
     * Test case for handing over process variables to a sub process
     */
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testSubProcessDataInputOutput.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testSubProcessWithDataInputOutput() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("superVariable", "Hello from the super process.");

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("subProcessDataInputOutput", vars);

        // one task in the subprocess should be active after starting the
        // process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task in subprocess");
        assertThat(runtimeService.getVariable(taskBeforeSubProcess.getProcessInstanceId(), "subVariable")).isEqualTo("Hello from the super process.");
        assertThat(taskService.getVariable(taskBeforeSubProcess.getId(), "subVariable")).isEqualTo("Hello from the super process.");

        runtimeService.setVariable(taskBeforeSubProcess.getProcessInstanceId(), "subVariable", "Hello from sub process.");

        // super variable is unchanged
        assertThat(runtimeService.getVariable(processInstance.getId(), "superVariable")).isEqualTo("Hello from the super process.");

        // Completing this task ends the subprocess which leads to a task in the
        // super process
        taskService.complete(taskBeforeSubProcess.getId());

        // one task in the subprocess should be active after starting the
        // process instance
        Task taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task in super process");
        assertThat(runtimeService.getVariable(processInstance.getId(), "superVariable")).isEqualTo("Hello from sub process.");
        assertThat(taskService.getVariable(taskAfterSubProcess.getId(), "superVariable")).isEqualTo("Hello from sub process.");

        vars.clear();
        vars.put("x", 5l);

        // Completing this task ends the super process which leads to a task in
        // the super process
        taskService.complete(taskAfterSubProcess.getId(), vars);

        // now we are the second time in the sub process but passed variables
        // via expressions
        Task taskInSecondSubProcess = taskQuery.singleResult();
        assertThat(taskInSecondSubProcess.getName()).isEqualTo("Task in subprocess");
        assertThat(runtimeService.getVariable(taskInSecondSubProcess.getProcessInstanceId(), "y")).isEqualTo(10l);
        assertThat(taskService.getVariable(taskInSecondSubProcess.getId(), "y")).isEqualTo(10l);

        // Completing this task ends the subprocess which leads to a task in the super process
        taskService.complete(taskInSecondSubProcess.getId());

        // one task in the subprocess should be active after starting the process instance
        Task taskAfterSecondSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSecondSubProcess.getName()).isEqualTo("Task in super process");
        assertThat(runtimeService.getVariable(taskAfterSecondSubProcess.getProcessInstanceId(), "z")).isEqualTo(15l);
        assertThat(taskService.getVariable(taskAfterSecondSubProcess.getId(), "z")).isEqualTo(15l);

        // and end last task in Super process
        taskService.complete(taskAfterSecondSubProcess.getId());

        assertProcessEnded(processInstance.getId());
        assertThat(runtimeService.createExecutionQuery().list()).isEmpty();
    }

    /**
     * Test case for deleting a sub process
     */
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testTwoSubProcesses.bpmn20.xml", "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testTwoSubProcesses() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callTwoSubProcesses");

        List<ProcessInstance> instanceList = runtimeService.createProcessInstanceQuery().list();
        assertThat(instanceList).isNotNull();
        assertThat(instanceList).hasSize(3);

        List<Task> taskList = taskService.createTaskQuery().list();
        assertThat(taskList).isNotNull();
        assertThat(taskList).hasSize(2);

        runtimeService.deleteProcessInstance(processInstance.getId(), "Test cascading");

        instanceList = runtimeService.createProcessInstanceQuery().list();
        assertThat(instanceList).isNotNull();
        assertThat(instanceList).isEmpty();

        taskList = taskService.createTaskQuery().list();
        assertThat(taskList).isNotNull();
        assertThat(taskList).isEmpty();
    }

    @Test
    @Deployment(resources = {
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testStartUserIdSetWhenLooping.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"
    })
    public void testStartUserIdSetWhenLooping() {
        identityService.setAuthenticatedUserId("kermit");
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("loopingCallActivity", CollectionUtil.singletonMap("input", 0));
        for (int i = 1; i < 4; i++) {
            Task task = taskService.createTaskQuery().singleResult();
            assertThat(task.getName()).isEqualTo("Task in subprocess");
            identityService.setAuthenticatedUserId("kermit");
            taskService.complete(task.getId(), CollectionUtil.singletonMap("input", i));
        }
        identityService.setAuthenticatedUserId(null);

        Task task = taskService.createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Final task");

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration, 30000)) {
            List<HistoricProcessInstance> historicProcessInstances = historyService.createHistoricProcessInstanceQuery()
                    .superProcessInstanceId(processInstance.getId()).list();
            assertThat(historicProcessInstances).hasSize(3);
            for (HistoricProcessInstance historicProcessInstance : historicProcessInstances) {
                assertThat(historicProcessInstance.getStartUserId()).isNotNull();
                assertThat(historicProcessInstance.getStartTime()).isNotNull();
                assertThat(historicProcessInstance.getEndTime()).isNotNull();
            }
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml", "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testAuthenticatedStartUserInCallActivity() {
        final String authenticatedUser = "user1";
        identityService.setAuthenticatedUserId(authenticatedUser);
        final ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSimpleSubProcess");

        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
        // Completing the task continues the process which leads to calling the subprocess
        taskService.complete(taskBeforeSubProcess.getId());

        ProcessInstance subProcess = runtimeService.createProcessInstanceQuery().superProcessInstanceId(processInstance.getId()).singleResult();

        assertThat(subProcess.getStartUserId()).isEqualTo(authenticatedUser);
        List<IdentityLink> subProcessIdentityLinks = runtimeService.getIdentityLinksForProcessInstance(subProcess.getId());
        assertThat(subProcessIdentityLinks).hasSize(1);
        assertThat(subProcessIdentityLinks.get(0).getType()).isEqualTo(IdentityLinkType.STARTER);
        assertThat(subProcessIdentityLinks.get(0).getUserId()).isEqualTo(authenticatedUser);
    }
    
    @Test
    @Deployment(resources = { 
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml", 
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" 
            })
    public void testDeleteProcessInstance() {
        // Bring process instance to task in child process instance
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSimpleSubProcess");
        Task taskBeforeSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
        
        // Delete child process instance: parent process instance should continue
        assertThat(processInstance.getId()).isNotEqualTo(taskInSubProcess.getProcessInstanceId());
        runtimeService.deleteProcessInstance(taskInSubProcess.getProcessInstanceId(), null);
        
        Task taskAfterSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskAfterSubProcess).isNotNull();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");
        
        taskService.complete(taskAfterSubProcess.getId());
        assertThat(runtimeService.createExecutionQuery().count()).isZero();
    }
    
    @Test
    @Deployment(resources = {
        "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml", 
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" 
    })
    public void testSubProcessEndTime() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("callSimpleSubProcess");
        Task taskBeforeSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        runtimeService.deleteProcessInstance(processInstance.getId(), null);

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration, 20000)) {
            List<HistoricProcessInstance> processes = historyService.createHistoricProcessInstanceQuery().list();
            assertThat(processes).hasSize(2);
    
            for (HistoricProcessInstance process: processes) {
                assertThat(process.getEndTime()).isNotNull();
            }
        }
    }
    
    @Test
    @Deployment(resources = {
        "org/flowable/engine/test/bpmn/callactivity/callActivityInEmbeddedSubProcess.bpmn20.xml", 
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml" 
    })
    public void testCallActivityInEmbeddedSubProcessEndTime() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("mainProcess");
        Task taskInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        runtimeService.deleteProcessInstance(processInstance.getId(), null);

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration, 20000)) {
            List<HistoricProcessInstance> processes = historyService.createHistoricProcessInstanceQuery().list();
            assertThat(processes).hasSize(2);
    
            for (HistoricProcessInstance process: processes) {
                assertThat(process.getEndTime()).isNotNull();
            }
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallback.bpmn20.xml"},
        tenantId = "flowable"
    )
    public void testCallSubProcessWithFallbackToDefaultTenant() {
        assertCallActivityToFallback();
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallbackWrongNonBoolean.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "flowable"
    )
    public void testCallSubProcessWithFallbackToDefaultTenantWithWrongExpressionOnSameTenant() {
        assertProcessExecuted();
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallbackFalse.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "flowable"
    )
    public void testCallSubProcessWithFallbackToDefaultTenantFalseInSameTenant() {
        assertProcessExecuted();
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallbackFalse.bpmn20.xml"},
        tenantId = "flowable"
    )
    public void testCallSubProcessWithFallbackToDefaultTenantFalse() {
        assertThrows(
            FlowableObjectNotFoundException.class,
            () -> assertCallActivityToFallback()
        );
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallbackWrongNonBoolean.bpmn20.xml"},
        tenantId = "flowable"
    )
    public void testCallSubProcessWithFallbackToDefaultTenantNonBooleanValue() {
        assertThrows(
            FlowableException.class,
            () -> assertCallActivityToFallback(),
            "Unable to recognize fallbackToDefaultTenant value 1"
        );
    }
    
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallback.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithDefaultTenantFallback() {
        DefaultTenantProvider originalDefaultTenantProvider = processEngineConfiguration.getDefaultTenantProvider();
        processEngineConfiguration.setDefaultTenantValue("defaultFlowable");
        try {
            ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someTenant")
                            .fallbackToDefaultTenant()
                            .start();
            
            assertThat(processInstance.getTenantId()).isEqualTo("someTenant");
    
            // one task in the subprocess should be active after starting the process instance
            TaskQuery taskQuery = taskService.createTaskQuery().taskTenantId("someTenant");
            Task taskInSubProcess = taskQuery.singleResult();
            assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
            assertThat(taskInSubProcess.getTenantId()).isEqualTo("someTenant");
    
            // Completing the task in the subprocess, finishes the subprocess
            taskService.complete(taskInSubProcess.getId());
            assertProcessEnded(processInstance.getId());
            
        } finally {
            processEngineConfiguration.setDefaultTenantProvider(originalDefaultTenantProvider);
        }
    }
    
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithGlobalDefaultTenantFallback() {
        DefaultTenantProvider originalDefaultTenantProvider = processEngineConfiguration.getDefaultTenantProvider();
        processEngineConfiguration.setDefaultTenantValue("defaultFlowable");
        processEngineConfiguration.setFallbackToDefaultTenant(true);
        try {
            ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someTenant")
                            .fallbackToDefaultTenant()
                            .start();
            
            assertThat(processInstance.getTenantId()).isEqualTo("someTenant");
    
            // one task in the subprocess should be active after starting the process instance
            Task taskBeforeSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
            assertThat(taskBeforeSubProcess.getTenantId()).isEqualTo("someTenant");
            taskService.complete(taskBeforeSubProcess.getId());
            
            Task taskInSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
            assertThat(taskInSubProcess.getTenantId()).isEqualTo("someTenant");
            
            // Delete child process instance: parent process instance should continue
            assertThat(processInstance.getId()).isNotEqualTo(taskInSubProcess.getProcessInstanceId());
            runtimeService.deleteProcessInstance(taskInSubProcess.getProcessInstanceId(), null);
            
            Task taskAfterSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskAfterSubProcess).isNotNull();
            assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");
            
            taskService.complete(taskAfterSubProcess.getId());
            
            assertProcessEnded(processInstance.getId());
            
        } finally {
            processEngineConfiguration.setDefaultTenantProvider(originalDefaultTenantProvider);
            processEngineConfiguration.setFallbackToDefaultTenant(false);
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithGlobalDefaultTenantFallbackAndComplexTenantFallback() {
        DefaultTenantProvider originalDefaultTenantProvider = processEngineConfiguration.getDefaultTenantProvider();
        processEngineConfiguration.setDefaultTenantProvider((tenantId, scope, scopeKey) -> {
            if ("someTenant".equals(tenantId)) {
                return "defaultFlowable";
            } else {
                return "defaultTest";
            }
        });
        processEngineConfiguration.setFallbackToDefaultTenant(true);
        try {
            ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someTenant")
                            .fallbackToDefaultTenant()
                            .start();

            assertThat(processInstance.getTenantId()).isEqualTo("someTenant");

            // one task in the subprocess should be active after starting the process instance
            Task taskBeforeSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");
            assertThat(taskBeforeSubProcess.getTenantId()).isEqualTo("someTenant");
            taskService.complete(taskBeforeSubProcess.getId());

            Task taskInSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
            assertThat(taskInSubProcess.getTenantId()).isEqualTo("someTenant");

            // Delete child process instance: parent process instance should continue
            assertThat(processInstance.getId()).isNotEqualTo(taskInSubProcess.getProcessInstanceId());
            runtimeService.deleteProcessInstance(taskInSubProcess.getProcessInstanceId(), null);

            Task taskAfterSubProcess = taskService.createTaskQuery().singleResult();
            assertThat(taskAfterSubProcess).isNotNull();
            assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

            taskService.complete(taskAfterSubProcess.getId());

            assertProcessEnded(processInstance.getId());

        } finally {
            processEngineConfiguration.setDefaultTenantProvider(originalDefaultTenantProvider);
            processEngineConfiguration.setFallbackToDefaultTenant(false);
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithGlobalDefaultTenantFallbackAndComplexTenantFallbackAndNotExistingDefaultTenant() {
        DefaultTenantProvider originalDefaultTenantProvider = processEngineConfiguration.getDefaultTenantProvider();
        processEngineConfiguration.setDefaultTenantProvider((tenantId, scope, scopeKey) -> {
            if ("someTenant".equals(tenantId)) {
                return "defaultFlowable";
            } else {
                return "defaultTest";
            }
        });
        processEngineConfiguration.setFallbackToDefaultTenant(true);
        try {
            Assertions.assertThatThrownBy(() -> {
                    runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someOtherTenant")
                            .fallbackToDefaultTenant()
                            .start();

            })
                .isInstanceOf(FlowableObjectNotFoundException.class)
                .hasMessage("No process definition found for key 'callSimpleSubProcess'. Fallback to default tenant was also applied.");
        } finally {
            processEngineConfiguration.setDefaultTenantProvider(originalDefaultTenantProvider);
            processEngineConfiguration.setFallbackToDefaultTenant(false);
        }
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcessWithFallback.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithDefaultTenantFallbackAndEmptyDefaultTenant() {
        try {
            runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someTenant")
                            .fallbackToDefaultTenant()
                            .start();
            fail("Expected process definition not found");
            
        } catch (FlowableObjectNotFoundException e) {
            // expected exception
        }
    }
    
    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/callactivity/CallActivity.testCallSimpleSubProcess.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"},
        tenantId = "defaultFlowable"
    )
    public void testCallSimpleSubProcessWithGlobalDefaultTenantFallbackAndEmptyDefaultTenant() {
        processEngineConfiguration.setFallbackToDefaultTenant(true);
        try {
            runtimeService.createProcessInstanceBuilder()
                            .processDefinitionKey("callSimpleSubProcess")
                            .tenantId("someTenant")
                            .fallbackToDefaultTenant()
                            .start();
            fail("Expected process definition not found");
            
        } catch (FlowableObjectNotFoundException e) {
            // expected exception
        } finally {
            processEngineConfiguration.setFallbackToDefaultTenant(false);
        }
    }

    @Test
    @Deployment(resources = {
            "org/flowable/engine/test/bpmn/callactivity/CallActivity.testAsyncSequentialMiCallActivity.bpmn20.xml",
            "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"
    })
    public void testAsyncSequentialMiCallActivity() {
        ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey("testAsyncMiCallActivity")
                .variable("myList", Arrays.asList("one", "two", "three"))
                .start();

        Job job = managementService.createJobQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(job).isNotNull();
        managementService.executeJob(job.getId());

        assertThat(taskService.createTaskQuery().count()).isEqualTo(1);
        Task task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        Job secondJob = managementService.createJobQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(secondJob.getId()).isNotSameAs(job.getId());
        managementService.executeJob(secondJob.getId());

        task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        Job thirdJob = managementService.createJobQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(thirdJob.getId()).isNotSameAs(secondJob.getId());
        managementService.executeJob(thirdJob.getId());

        task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        assertProcessEnded(processInstance.getId());
    }

    @Test
    @Deployment(resources = {
        "org/flowable/engine/test/bpmn/callactivity/CallActivity.testIdVariableName.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"
    })
    public void testIdVariableName() {
        ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey("testIdVariableName")
            .start();

        Task task = taskService.createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Task in subprocess");

        assertThat(runtimeService.getVariables(processInstance.getId())).hasSize(1);
        assertThat(runtimeService.getVariables(task.getProcessInstanceId())).isEmpty();

        assertThat(runtimeService.getVariable(processInstance.getId(), "myVariable")).isEqualTo(task.getProcessInstanceId());
    }

    @Test
    @Deployment(resources = {
        "org/flowable/engine/test/bpmn/callactivity/CallActivity.testIdVariableNameExpression.bpmn20.xml",
        "org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml"
    })
    public void testIdVariableNameExpression() {
        ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey("testIdVariableName")
            .variable("counter", 123)
            .start();

        Task task = taskService.createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Task in subprocess");

        assertThat(runtimeService.getVariables(processInstance.getId())).hasSize(2);
        assertThat(runtimeService.getVariables(task.getProcessInstanceId())).isEmpty();

        assertThat(runtimeService.getVariable(processInstance.getId(), "myVariable-123")).isEqualTo(task.getProcessInstanceId());
    }

    protected void assertCallActivityToFallback() {
        org.flowable.engine.repository.Deployment deployment = this.repositoryService.createDeployment().
            addClasspathResource("org/flowable/engine/test/bpmn/callactivity/simpleSubProcess.bpmn20.xml").
            tenantId(ProcessEngineConfiguration.NO_TENANT_ID).
            deploy();

        try {
            assertProcessExecuted();
        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }

    protected void assertProcessExecuted() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKeyAndTenantId("callSimpleSubProcess", "flowable");

        Task taskInSubProcess = taskService.createTaskQuery().singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");
        assertThat(taskInSubProcess.getTenantId()).isEqualTo("flowable");

        // Completing the task in the subprocess, finishes the processes
        taskService.complete(taskInSubProcess.getId());
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();
    }

}
