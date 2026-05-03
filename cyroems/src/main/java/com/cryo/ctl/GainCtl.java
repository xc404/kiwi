//package com.cryo.ctl;
//
//import com.cryo.dao.GainRepository;
//import com.cryo.model.Gain;
//import lombok.RequiredArgsConstructor;
//import net.dreamlu.mica.core.result.R;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.ResponseBody;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequiredArgsConstructor
//public class GainCtl {
//
//    private final GainRepository gainRepository;
//
//    @GetMapping("api/gains")
//    @ResponseBody
//    public R<Page<Gain>> gains(Pageable pageable){
//        Page<Gain> all = this.gainRepository.findAll(pageable);
//        return R.success(all);
//    }
//}
