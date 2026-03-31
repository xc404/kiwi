package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectDao;
import com.kiwi.project.bpm.model.BpmProject;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequestMapping("bpm/project")
@RequiredArgsConstructor
public class BpmProjectCtl extends BaseCtl
{


    private final BpmProjectDao bpmProjectDao;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;

    @GetMapping("")
    @ResponseBody
    public Page<BpmProject> getProjects(Pageable pageable) {
        return bpmProjectDao.findAll(pageable);

    }

    // 按ID查询
    @GetMapping("/{id}")
    @ResponseBody
    public BpmProject get(@PathVariable String id) {
        return bpmProjectDao.findById(id).orElse(null);
    }

    // 新增文件夹
    @PostMapping("")
    @ResponseBody
    public BpmProject add(@RequestBody BpmProject folder) {
        return bpmProjectDao.save(folder);
    }

    // 更新文件夹
    @PutMapping("{id}")
    @ResponseBody
    public BpmProject update(@PathVariable String id, @RequestBody BpmProject bpmProject) {
        bpmProjectDao.updateSelective(bpmProject);
        return bpmProject;
    }

    // 删除文件夹
    @DeleteMapping("{id}")
    @ResponseBody
    public void delete(@PathVariable String id) {
        bpmProjectDao.deleteById(id);
    }
}