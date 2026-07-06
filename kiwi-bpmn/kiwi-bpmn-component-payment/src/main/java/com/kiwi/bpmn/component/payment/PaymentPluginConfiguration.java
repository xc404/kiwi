package com.kiwi.bpmn.component.payment;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = PaymentCreateActivity.class)
public class PaymentPluginConfiguration {}
