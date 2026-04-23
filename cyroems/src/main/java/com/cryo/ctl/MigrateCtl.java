//package com.cryo.ctl;
//
//import com.cryo.data.migrate.DataSetMigrator;
//import com.cryo.data.migrate.MovieMigrator;
//import com.cryo.data.migrate.TaskMigrator;
//import com.cryo.data.migrate.UserMigrator;
//import lombok.RequiredArgsConstructor;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.time.DateUtils;
//import org.springframework.data.domain.Sort;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//import java.text.ParseException;
//import java.util.Date;
//
//@Controller
//@RequiredArgsConstructor
//public class MigrateCtl
//{
//    private final TaskMigrator taskMigrator;
//    private final DataSetMigrator dataSetMigrator;
//    //    private final GainMigrator gainMigrator;
////    private final MovieMigrator movieMigrator;
//    private final UserMigrator userMigrator;
//
//    //    private final GroupMigrator groupMigrator;
//    @PostMapping("/dev/task/migrate")
//    @ResponseBody
//    public void migrateTask(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset) {
//        this.taskMigrator.migrate(reset);
//    }
//
//    @PostMapping("/dev/data/migrate")
//    @ResponseBody
//    public void migrateData(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset) {
//        this.dataSetMigrator.migrate(reset);
//    }
//
//    //
////    @PostMapping("/dev/gain/migrate")
////    @ResponseBody
////    public void migrateGain(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset) {
////        this.gainMigrator.migrate(reset);
////    }
////    @PostMapping("/dev/movie/migrate")
////    @ResponseBody
////    public void migrateMovie(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset,
////                             @RequestParam(name = "date", required = false, defaultValue = "20250628") String date,
////                             @RequestParam(name = "taskId", required = false, defaultValue = "") String taskId,
////                             @RequestParam(name = "newly", required = false, defaultValue = "true") boolean newly
////    ) {
////        Query query = new Query();
////        if( StringUtils.isBlank(taskId) ) {
////
////
////            if( StringUtils.isBlank(date) ) {
////                date = "20250628";
////            }
////            Date time;
////            try {
////                time = DateUtils.parseDate(date, "yyyyMMdd");
////            } catch( ParseException e ) {
////                throw new RuntimeException(e);
////            }
////
//////        long l = time.getTime() / 1000;
////            if( newly ) {
////                query.addCriteria(Criteria.where("created_at").gte(time));
////            } else {
////                query.addCriteria(Criteria.where("created_at").lte(time));
////            }
////        }else{
////            query.addCriteria(Criteria.where("id").is(taskId));
////        }
////        query.with(Sort.by(Sort.Order.desc("created_at")));
////        this.movieMigrator.migrate(reset, query);
////    }
//
//    //
//    @PostMapping("/dev/user/migrate")
//    @ResponseBody
//    public void migrateUser(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset) {
//        this.userMigrator.migrate(reset);
//    }
////
////    @PostMapping("/dev/group/migrate")
////    @ResponseBody
////    public void migrateGroup(@RequestParam(name = "reset", required = false, defaultValue = "true") boolean reset) {
////        this.groupMigrator.migrate(reset);
////    }
//
//
//}
