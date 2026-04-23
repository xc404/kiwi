package com.cryo.service.cmd;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSpace
{
    private final SoftwareService softwareService;


    public long getUserSpace(String user)
    {
        SoftwareService.CmdProcess userSpace = softwareService.getUserSpace(user);
        try {
            userSpace.startAndWait();
        }catch( CmdException e ){
            return Long.MAX_VALUE;
        }
        String result = userSpace.result();
        if(result.contains("User not found")){
            return Long.MAX_VALUE;
        }
        List<String> list = result.lines().toList();
        if(list.size() != 3){
            return  Long.MAX_VALUE;
        }
        String s = StringUtils.trim(list.get(2));
        if( !NumberUtils.isCreatable(s) ){
            return  Long.MAX_VALUE;
        }
        return (long) (Double.parseDouble(s)*(Math.pow(1024, 4)));
    }
}
