# kiwi-bpmn-component-s3

独立可选模块：`S3ObjectActivity`（`@Component("s3Object")`），兼容 MinIO（`endpoint` + path-style）。

## 接入

```xml
<dependency>
    <groupId>com.kiwi</groupId>
    <artifactId>kiwi-bpmn-component-s3</artifactId>
</dependency>
```

AWS SDK v2 仅在引入本模块时进入 classpath。
