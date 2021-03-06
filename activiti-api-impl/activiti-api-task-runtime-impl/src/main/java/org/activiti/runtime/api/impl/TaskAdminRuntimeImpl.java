/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.runtime.api.impl;

import java.util.ArrayList;
import java.util.List;

import org.activiti.api.runtime.shared.NotFoundException;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.model.impl.TaskImpl;
import org.activiti.api.task.model.payloads.AssignTaskPayload;
import org.activiti.api.task.model.payloads.CandidateGroupsPayload;
import org.activiti.api.task.model.payloads.CandidateUsersPayload;
import org.activiti.api.task.model.payloads.ClaimTaskPayload;
import org.activiti.api.task.model.payloads.CompleteTaskPayload;
import org.activiti.api.task.model.payloads.DeleteTaskPayload;
import org.activiti.api.task.model.payloads.GetTasksPayload;
import org.activiti.api.task.model.payloads.ReleaseTaskPayload;
import org.activiti.api.task.model.payloads.SetTaskVariablesPayload;
import org.activiti.api.task.model.payloads.UpdateTaskPayload;
import org.activiti.api.task.runtime.TaskAdminRuntime;
import org.activiti.engine.TaskService;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.TaskQuery;
import org.activiti.runtime.api.model.impl.APITaskConverter;
import org.activiti.runtime.api.query.impl.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasRole('ACTIVITI_ADMIN')")
public class TaskAdminRuntimeImpl implements TaskAdminRuntime {

    private final TaskService taskService;

    private final APITaskConverter taskConverter;

    public TaskAdminRuntimeImpl(TaskService taskService,
                                APITaskConverter taskConverter) {
        this.taskService = taskService;
        this.taskConverter = taskConverter;
    }

    @Override
    public Task task(String taskId) {
        org.activiti.engine.task.Task internalTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (internalTask == null) {
            throw new NotFoundException("Unable to find task for the given id: " + taskId);
        }
        return taskConverter.from(internalTask);
    }

    @Override
    public Page<Task> tasks(Pageable pageable) {
        return tasks(pageable,
                     TaskPayloadBuilder.tasks().build());
    }

    @Override
    public Page<Task> tasks(Pageable pageable,
                            GetTasksPayload getTasksPayload) {
        TaskQuery taskQuery = taskService.createTaskQuery();

        if (getTasksPayload.getProcessInstanceId() != null) {
            taskQuery = taskQuery.processInstanceId(getTasksPayload.getProcessInstanceId());
        }
        if (getTasksPayload.getParentTaskId() != null) {
            taskQuery = taskQuery.taskParentTaskId(getTasksPayload.getParentTaskId());
        }

        List<Task> tasks = taskConverter.from(taskQuery.listPage(pageable.getStartIndex(),
                                                                 pageable.getMaxItems()));
        return new PageImpl<>(tasks,
                              Math.toIntExact(taskQuery.count()));
    }

    @Override
    public Task delete(DeleteTaskPayload deleteTaskPayload) {

        //      we might need to create an empty shell with the task ID and Status only
        Task task = task(deleteTaskPayload.getTaskId());

        TaskImpl deletedTaskData = new TaskImpl(task.getId(),
                                                task.getName(),
                                                Task.TaskStatus.DELETED);
        taskService.deleteTask(deleteTaskPayload.getTaskId(),
                               deleteTaskPayload.getReason(),
                               true);
        return deletedTaskData;
    }

    @Override
    public void setVariables(SetTaskVariablesPayload setTaskVariablesPayload) {
        taskService.setVariablesLocal(setTaskVariablesPayload.getTaskId(),
                                          setTaskVariablesPayload.getVariables());

    }

    @Override
    public Task complete(CompleteTaskPayload completeTaskPayload) {
        Task task = task(completeTaskPayload.getTaskId());
        if (task == null) {
            throw new IllegalStateException("Task with id: " + completeTaskPayload.getTaskId() + " cannot be completed because it cannot be found.");
        }
        TaskImpl competedTaskData = new TaskImpl(task.getId(),
                                                 task.getName(),
                                                 Task.TaskStatus.COMPLETED);
        taskService.complete(completeTaskPayload.getTaskId(),
                             completeTaskPayload.getVariables(),true);
        return competedTaskData;
    }

    @Override
    public Task claim(ClaimTaskPayload claimTaskPayload) {
        taskService.claim(claimTaskPayload.getTaskId(),
                          claimTaskPayload.getAssignee());

        return task(claimTaskPayload.getTaskId());
    }

    @Override
    public Task release(ReleaseTaskPayload releaseTaskPayload) {
        taskService.unclaim(releaseTaskPayload.getTaskId());
        return task(releaseTaskPayload.getTaskId());
    }
    
    @Override
    public Task assign(AssignTaskPayload assignTaskPayload) {
        //We need to release, claim for assigned task is not working!
        taskService.unclaim(assignTaskPayload.getTaskId());
        
        //Now assign a new user
        taskService.claim(assignTaskPayload.getTaskId(),
                          assignTaskPayload.getAssignee());
        
        return task(assignTaskPayload.getTaskId());
    }
    
    @Override
    public void addCandidateUsers(CandidateUsersPayload candidateUsersPayload) {      
       if (candidateUsersPayload.getCandidateUsers() != null && !candidateUsersPayload.getCandidateUsers().isEmpty()) {
            for (String u : candidateUsersPayload.getCandidateUsers()) {
                taskService.addCandidateUser(candidateUsersPayload.getTaskId(),
                                             u);
            }
       }
    }
    
    @Override
    public void deleteCandidateUsers(CandidateUsersPayload candidateUsersPayload) {           
        if (candidateUsersPayload.getCandidateUsers() != null && !candidateUsersPayload.getCandidateUsers().isEmpty()) {
            for (String u : candidateUsersPayload.getCandidateUsers()) {
                taskService.deleteCandidateUser(candidateUsersPayload.getTaskId(),
                                                u);
            }
        }
    }
    
    @Override
    public void addCandidateGroups(CandidateGroupsPayload candidateGroupsPayload) {          
       if (candidateGroupsPayload.getCandidateGroups() != null && !candidateGroupsPayload.getCandidateGroups().isEmpty()) {
            for (String g : candidateGroupsPayload.getCandidateGroups()) {
                taskService.addCandidateGroup(candidateGroupsPayload.getTaskId(),
                                              g);
            }
       }
    }
    
    @Override
    public void deleteCandidateGroups(CandidateGroupsPayload candidateGroupsPayload) {    
        if (candidateGroupsPayload.getCandidateGroups() != null && !candidateGroupsPayload.getCandidateGroups().isEmpty()) {
            for (String g : candidateGroupsPayload.getCandidateGroups()) {
                taskService.deleteCandidateGroup(candidateGroupsPayload.getTaskId(),
                                                 g);
            }
        }
    }
    
    @Override
    public List<String> userCandidates(String taskId) {
        List<IdentityLink> identityLinks= getIdentityLinks(taskId);
        List<String> userCandidates = new ArrayList<String>();
        if (identityLinks!=null) {
            for (IdentityLink i : identityLinks) {
                if (i.getUserId()!=null) {
                    if (i.getType().equals(IdentityLinkType.CANDIDATE) ) {
                        userCandidates.add(i.getUserId());
                    } 
                }
            }
            
        }
        return userCandidates;
    }
    
    @Override
    public List<String> groupCandidates(String taskId) {
        List<IdentityLink> identityLinks= getIdentityLinks(taskId);
        List<String> groupCandidates = new ArrayList<String>();
        if (identityLinks!=null) {
            for (IdentityLink i : identityLinks) {
                if (i.getGroupId()!=null) {
                    if (i.getType().equals(IdentityLinkType.CANDIDATE) ) {
                        groupCandidates.add(i.getGroupId());
                    } 
                }
            }

        }
        return groupCandidates;
    }
    
    private List<IdentityLink> getIdentityLinks(String taskId) {
        return taskService.getIdentityLinksForTask(taskId);
    }

    @Override
    public Task update(UpdateTaskPayload updateTaskPayload) {
        throw new IllegalArgumentException("To be implemented");
    }
}
