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
package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.BpmnBehaviorLogger;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.commons.utils.IoUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ComponentDescription(name = "Shell", description = "Execute a shell command"

            , inputs = {
            @ComponentParameter(key = "command", htmlType = "#text", description = "The shell command to execute"),
            @ComponentParameter(key = "directory", htmlType = "#text", description = "The working directory for the command"),
            @ComponentParameter(key = "waitFlag", htmlType = "CheckBox", description = "Whether to wait for the command to finish before proceeding"),
            @ComponentParameter(key = "redirectError", htmlType = "CheckBox", description = "Whether to redirect error stream to output stream"),
            @ComponentParameter(key = "cleanEnv", htmlType = "CheckBox", description = "Whether to clear environment variables for the command")
    }
            , outputs = {
            @ComponentParameter(key = "result", htmlType = "#text", description = "The output of the shell command"),
            @ComponentParameter(key = "errorCode", htmlType = "#text", description = "The error code returned by the shell command")
    }

)
@Component("shellActivityBehavior")
public class ShellActivityBehavior implements JavaDelegate
{

  protected static final BpmnBehaviorLogger LOG = ProcessEngineLogger.BPMN_BEHAVIOR_LOGGER;



  @Override
  public void execute(DelegateExecution execution) {

      String commandStr = execution.getVariableLocal("command").toString();
//    String directoryStr = execution.getVariableLocal("directory").toString();
//        Boolean waitFlag = Boolean.valueOf(execution.getVariableLocal("waitFlag").toString());
//        Boolean redirectErrorFlag = Boolean.valueOf(execution.getVariableLocal("redirectError").toString());
//        Boolean cleanEnv = Boolean.valueOf(execution.getVariableLocal("cleanEnv").toString());
//        String resultVariableStr = execution.getVariableLocal("resultVariable").toString();
      List<String> argList = new ArrayList<String>(Arrays.stream(commandStr.split(" ")).toList());
//      getStringFromField()

    String resultVariableStr = ExecutionUtils.getOutputVariableName(execution,"result");
    String errorCodeVariableStr = ExecutionUtils.getOutputVariableName(execution,"errorCode");
    Boolean waitFlag = ExecutionUtils.getBooleanInputVariable(execution,"waitFlag").orElse(true);
    Boolean redirectErrorFlag = ExecutionUtils.getBooleanInputVariable(execution,"redirectError").orElse(false);
    Boolean cleanEnv = ExecutionUtils.getBooleanInputVariable(execution,"cleanEnv").orElse(false);
    String directoryStr = ExecutionUtils.getStringInputVariable(execution,"directory").orElse(null);

    ProcessBuilder processBuilder = new ProcessBuilder(argList);

    try {
      processBuilder.redirectErrorStream(redirectErrorFlag);
      if (cleanEnv) {
        Map<String, String> env = processBuilder.environment();
        env.clear();
      }
      if (directoryStr != null && !directoryStr.isEmpty() )
        processBuilder.directory(new File(directoryStr));

      Process process = processBuilder.start();

      if (waitFlag) {
        int errorCode = process.waitFor();

        if (resultVariableStr != null) {
          String result = convertStreamToStr(process.getInputStream());
          execution.setVariable(resultVariableStr, result);
        }

        if (errorCodeVariableStr != null) {
          execution.setVariable(errorCodeVariableStr, Integer.toString(errorCode));

        }

      }
    } catch (Exception e) {
      throw LOG.shellExecutionException(e);
    }

  }

  public static String convertStreamToStr(InputStream is) throws IOException {

    return IoUtil.inputStreamAsString(is);
  }


}
