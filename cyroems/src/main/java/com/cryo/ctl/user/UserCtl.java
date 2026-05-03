package com.cryo.ctl.user;


import com.cryo.common.query.QueryParams;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.User;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("api/user")
@RequiredArgsConstructor
public class UserCtl
{

    private final UserRepository userRepository;
    private final SessionService sessionService;

    public static class UserQuery
    {
        public String name;
    }


    @GetMapping("/api/users")
    @ResponseBody
    public Page<User> getUsers(UserQuery userQuery,
                               Pageable pageable) {


        SessionUser sessionUser = sessionService.getSessionUser();
        Query mongo = QueryParams.from(userQuery).toMongo();
        switch( sessionUser.getUser().getRole() ) {
            case "normal":
            case "group_admin":
                mongo.addCriteria(Criteria.where("group").is(sessionUser.getUser().getUser_group()));
        }
        return this.userRepository.findByQuery(mongo, pageable);
    }

//    @PostMapping
//    @ResponseBody
//    public User update(@RequestBody User user) {
//        this.userRepository.save(user);
//        return user;
//    }
}
