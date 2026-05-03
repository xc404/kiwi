package com.cryo.task.tilt.parse;

import com.cryo.common.error.FatalException;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.model.dataset.MDoc;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@RequiredArgsConstructor
@Service
public class MDocParseHandler implements Handler<MDocContext>
{
    private final MDocRepository mDocRepository;
    private MDocParser parser = new MDocParser();

    @Override
    public HandlerKey support() {
        return HandlerKey.MdodParser;
    }

    @Override
    public StepResult handle(MDocContext context) {
        MDoc mDoc = context.getMDoc();
//        if( mDoc.getMeta() != null && !context.getInstance().isForceReset() ) {
//            return StepResult.success("mdoc file parsed, skip");
//        }
        File file = new File(mDoc.getPath());
        if(!file.exists()){
            throw new FatalException("mdoc file not exists");
        }
        MDocMeta meta = parser.parse(file);
        mDoc.setMeta(meta);
        this.mDocRepository.save(mDoc);
        return StepResult.success("mdoc file parse success");
    }
}
