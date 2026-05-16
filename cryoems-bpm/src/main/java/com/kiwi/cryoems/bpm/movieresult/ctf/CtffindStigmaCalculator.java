package com.kiwi.cryoems.bpm.movieresult.ctf;

import com.kiwi.cryoems.bpm.model.ctf.EstimationResult;
import com.kiwi.cryoems.bpm.support.MicroscopeScaleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 对齐 cyroems {@code Ctffind5Support#calculate_stigma}。
 */
@Component
@RequiredArgsConstructor
public class CtffindStigmaCalculator {

    private final MicroscopeScaleRegistry microscopeScaleRegistry;

    public void calculate(String microscopeKey, EstimationResult estimationResult) {
        if (!StringUtils.hasText(microscopeKey) || estimationResult == null) {
            return;
        }
        MicroscopeScaleRegistry.TemStigma temStigma = microscopeScaleRegistry.temStigma(microscopeKey.trim());
        if (temStigma == null) {
            return;
        }
        double xTemstigmaAngle = temStigma.xTemstigmaAngle();
        double yTemstigmaAngle = temStigma.yTemstigmaAngle();
        double xTemstigmaStep = temStigma.xTemstigmaStep();
        double yTemstigmaStep = temStigma.yTemstigmaStep();

        double stigmaAngle = estimationResult.getAzimuth_of_astigmatism();
        double xAngleDiff = xTemstigmaAngle - stigmaAngle;
        double defocusU = estimationResult.getDefocus_1();
        double defocusV = estimationResult.getDefocus_2();
        double kx1 = Math.sin(Math.toRadians(xAngleDiff)) / Math.cos(Math.toRadians(xAngleDiff));
        xAngleDiff += 90;
        double kx2 = Math.sin(Math.toRadians(xAngleDiff)) / Math.cos(Math.toRadians(xAngleDiff));

        double lxSub1 = Math.sqrt((kx1 * kx1 + 1) / (defocusV * defocusV + defocusU * defocusU * kx1 * kx1));
        double lxSub2 = Math.sqrt((kx2 * kx2 + 1) / (defocusV * defocusV + defocusU * defocusU * kx2 * kx2));
        double lx = 0.0001 * defocusU * defocusV * (lxSub1 - lxSub2) / xTemstigmaStep;

        double yAngleDiff = yTemstigmaAngle - stigmaAngle;
        double ky1 = Math.sin(Math.toRadians(yAngleDiff)) / Math.cos(Math.toRadians(yAngleDiff));
        yAngleDiff += 90;
        double ky2 = Math.sin(Math.toRadians(yAngleDiff)) / Math.cos(Math.toRadians(yAngleDiff));

        double lySub1 = Math.sqrt((ky1 * ky1 + 1) / (defocusV * defocusV + defocusU * defocusU * ky1 * ky1));
        double lySub2 = Math.sqrt((ky2 * ky2 + 1) / (defocusV * defocusV + defocusU * defocusU * ky2 * ky2));
        double ly = 0.0001 * defocusU * defocusV * (lySub1 - lySub2) / yTemstigmaStep;

        estimationResult.setStigma_x(-ly);
        estimationResult.setStigma_y(-lx);
    }
}
