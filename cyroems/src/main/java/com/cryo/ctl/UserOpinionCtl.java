package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.cryo.dao.UserOpinionRepository;
import com.cryo.model.UserOpinion;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class UserOpinionCtl
{
    private final UserOpinionRepository userOpinionRepository;

    @PostMapping("/api/useropinion")
    @ResponseBody
    public UserOpinion userOpinion(@RequestBody UserOpinion userOpinion) {
        String loginId = StpUtil.getLoginId("");
        userOpinion.setUserId(loginId);
        this.userOpinionRepository.save(userOpinion);
        return userOpinion;
    }

    @GetMapping("/api/useropinion")
    @SaCheckLogin
    @ResponseBody
    public Page<UserOpinion> userOpinions(Pageable pageable) {
        Query query = new Query();

        query.with(Sort.by(Sort.Order.desc("created_at")));
        return this.userOpinionRepository.findByQuery(query, pageable);
    }
}
