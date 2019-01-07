package com.netflix.conductor.common.constraints;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.ConstraintParamUtil;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things.
 * 1. WorkflowDef is valid or not
 * 2. Make sure taskReferenceName used across different tasks are unique
 * 3. Verify inputParameters points to correct tasks or not
 */
@Documented
@Constraint(validatedBy = TaskReferenceNameUniqueConstraint.TaskReferenceNameUniqueValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskReferenceNameUniqueConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskReferenceNameUniqueValidator implements ConstraintValidator<TaskReferenceNameUniqueConstraint, WorkflowDef> {

        @Override
        public void initialize(TaskReferenceNameUniqueConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(WorkflowDef workflowDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            //check if taskReferenceNames are unique across tasks or not
            HashMap<String, Integer> taskReferenceMap = new HashMap<>();
            for (WorkflowTask workflowTask : workflowDef.getTasks()) {
                if (taskReferenceMap.containsKey(workflowTask.getTaskReferenceName())) {
                    String message = String.format("taskReferenceName: %s should be unique across tasks for a given workflowDefinition: %s",
                            workflowTask.getTaskReferenceName(), workflowDef.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                    valid = false;
                } else {
                    taskReferenceMap.put(workflowTask.getTaskReferenceName(), 1);
                }
            }
            //check inputParameters points to valid taskDef
            return valid & verifyTaskInputParameters(context, workflowDef);
        }

        private boolean verifyTaskInputParameters(ConstraintValidatorContext context, WorkflowDef workflow) {
            MutableBoolean valid = new MutableBoolean();
            valid.setValue(true);

            if (workflow.getTasks() == null) {
                return valid.getValue();
            }

            workflow.getTasks()
                    .stream()
                    .filter(workflowTask -> workflowTask.getInputParameters() != null)
                    .forEach(workflowTask -> {

                        workflowTask.getInputParameters()
                                .forEach((key, inputParam) -> {
                                    String paramPath = Objects.toString(inputParam, "");
                                    String[] paramPathComponents = ConstraintParamUtil.extractParamPathComponents(paramPath);
                                    if (paramPathComponents != null) {
                                        String source = paramPathComponents[0];    //workflow, or task reference name
                                        if (!"workflow".equals(source)) {
                                            WorkflowTask task = workflow.getTaskByRefName(source);
                                            if (task == null) {
                                                valid.setValue(false);
                                                String message = String.format("taskReferenceName: %s for given task: %s input value: %s of input parameter: %s" +
                                                        " is not defined in workflow definition.", source,  workflowTask.getName(), key, paramPath);
                                                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                                            }
                                        }
                                    }
                                });
                    });

            return valid.getValue();
        }
    }
}