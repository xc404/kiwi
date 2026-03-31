package com.kiwi.project.bpm.ctl;

import com.kiwi.framework.error.ExceptionHandler;
import com.kiwi.framework.security.SaTokenConfigure;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BpmProcessDefinitionCtl.class)
@Import({SaTokenConfigure.class, ExceptionHandler.class})
class BpmProcessDefinitionCtlSecurityTest {

    @MockBean
    private BpmProcessDefinitionDao bpmProcessDefinitionDao;
    @MockBean
    private BpmProcessDefinitionService bpmProcessDefinitionService;
    @MockBean
    private ProcessEngine processEngine;
    @MockBean
    private SessionService sessionService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listProcessesWithoutTokenReturnsUnifiedTokenError() throws Exception {
        mockMvc.perform(get("/bpm/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
