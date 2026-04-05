package com.kiwi.common.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CacheProvider;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.cfg.DatatypeFeature;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MutableCoercionConfig;
import com.fasterxml.jackson.databind.cfg.MutableConfigOverride;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonschema.JsonSchema;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.experimental.Delegate;
import lombok.experimental.UtilityClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全项目共享的 Jackson {@link ObjectMapper} 门面：通过静态方法委托 {@link ObjectMapper} 的全部公开实例方法。
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    public static ObjectMapper copy() {
        return OBJECT_MAPPER.copy();
    }

    public static int mixInCount() {
        return OBJECT_MAPPER.mixInCount();
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping(ObjectMapper.DefaultTyping applicability, JsonTypeInfo.As includeAs) {
        return OBJECT_MAPPER.enableDefaultTyping(applicability, includeAs);
    }

    public static ObjectWriter writer(FormatSchema schema) {
        return OBJECT_MAPPER.writer(schema);
    }

    public static JsonParser createParser(byte[] content, int offset, int len) throws IOException {
        return OBJECT_MAPPER.createParser(content, offset, len);
    }

    public static ObjectMapper setBase64Variant(Base64Variant v) {
        return OBJECT_MAPPER.setBase64Variant(v);
    }

    public static <T> T readValue(JsonParser p, ResolvedType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(p, valueType);
    }

    public static ObjectReader reader(DeserializationFeature feature) {
        return OBJECT_MAPPER.reader(feature);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping(ObjectMapper.DefaultTyping dti) {
        return OBJECT_MAPPER.enableDefaultTyping(dti);
    }

    public static ObjectMapper setTypeFactory(TypeFactory f) {
        return OBJECT_MAPPER.setTypeFactory(f);
    }

    public static ObjectMapper enable(JsonGenerator.Feature... features) {
        return OBJECT_MAPPER.enable(features);
    }

    public static <T> T readValue(File src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectMapper setSubtypeResolver(SubtypeResolver str) {
        return OBJECT_MAPPER.setSubtypeResolver(str);
    }

    public static void writeTree(JsonGenerator g, JsonNode rootNode) throws IOException {
        OBJECT_MAPPER.writeTree(g, rootNode);
    }

    public static ObjectReader reader(JsonNodeFactory nodeFactory) {
        return OBJECT_MAPPER.reader(nodeFactory);
    }

    public static ObjectReader readerForUpdating(Object valueToUpdate) {
        return OBJECT_MAPPER.readerForUpdating(valueToUpdate);
    }

    public static JsonNode readTree(String content) throws JsonProcessingException, JsonMappingException {
        return OBJECT_MAPPER.readTree(content);
    }

    public static ObjectMapper setPolymorphicTypeValidator(PolymorphicTypeValidator ptv) {
        return OBJECT_MAPPER.setPolymorphicTypeValidator(ptv);
    }

    public static ObjectMapper setDateFormat(DateFormat dateFormat) {
        return OBJECT_MAPPER.setDateFormat(dateFormat);
    }

    public static <T> T readValue(URL src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectMapper setCacheProvider(CacheProvider cacheProvider) {
        return OBJECT_MAPPER.setCacheProvider(cacheProvider);
    }

    public static <T> T readValue(byte[] src, int offset, int len, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, offset, len, valueType);
    }

    public static ObjectWriter writerFor(Class<?> rootType) {
        return OBJECT_MAPPER.writerFor(rootType);
    }

    public static ObjectMapper setDefaultVisibility(JsonAutoDetect.Value vis) {
        return OBJECT_MAPPER.setDefaultVisibility(vis);
    }

    public static JsonNode readTree(File file) throws IOException {
        return OBJECT_MAPPER.readTree(file);
    }

    public static boolean isEnabled(JsonGenerator.Feature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static String writeValueAsString(Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    public static ObjectMapper registerModules(Iterable<? extends Module> modules) {
        return OBJECT_MAPPER.registerModules(modules);
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability) {
        return OBJECT_MAPPER.activateDefaultTyping(ptv, applicability);
    }

    public static <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException, JsonMappingException {
        return OBJECT_MAPPER.readValue(content, valueType);
    }

    public static DeserializationContext getDeserializationContext() {
        return OBJECT_MAPPER.getDeserializationContext();
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv) {
        return OBJECT_MAPPER.activateDefaultTyping(ptv);
    }

    public static <T> T treeToValue(TreeNode n, TypeReference<T> toValueTypeRef) throws IllegalArgumentException, JsonProcessingException {
        return OBJECT_MAPPER.treeToValue(n, toValueTypeRef);
    }

    public static ObjectWriter writer(FilterProvider filterProvider) {
        return OBJECT_MAPPER.writer(filterProvider);
    }

    public static ObjectMapper disable(DeserializationFeature first, DeserializationFeature... f) {
        return OBJECT_MAPPER.disable(first, f);
    }

    @Deprecated
    public static JsonFactory getJsonFactory() {
        return OBJECT_MAPPER.getJsonFactory();
    }

    public static Class<?> findMixInClassFor(Class<?> cls) {
        return OBJECT_MAPPER.findMixInClassFor(cls);
    }

    public static JsonParser createParser(byte[] content) throws IOException {
        return OBJECT_MAPPER.createParser(content);
    }

    public static ObjectReader readerForMapOf(Class<?> type) {
        return OBJECT_MAPPER.readerForMapOf(type);
    }

    @Deprecated
    public static ObjectReader reader(TypeReference<?> type) {
        return OBJECT_MAPPER.reader(type);
    }

    public static JsonNode readTree(Reader r) throws IOException {
        return OBJECT_MAPPER.readTree(r);
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) throws IllegalArgumentException {
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    public static ObjectMapper addMixIn(Class<?> target, Class<?> mixinSource) {
        return OBJECT_MAPPER.addMixIn(target, mixinSource);
    }

    public static ObjectMapper configure(JsonParser.Feature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static <T> T readValue(Reader src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    @Deprecated
    public static void setVisibilityChecker(VisibilityChecker<?> vc) {
        OBJECT_MAPPER.setVisibilityChecker(vc);
    }

    @Deprecated
    public static ObjectReader reader(JavaType type) {
        return OBJECT_MAPPER.reader(type);
    }

    public static MutableCoercionConfig coercionConfigDefaults() {
        return OBJECT_MAPPER.coercionConfigDefaults();
    }

    public static ObjectMapper configure(DeserializationFeature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, JavaType valueType) throws IOException {
        return OBJECT_MAPPER.readValues(p, valueType);
    }

    @Deprecated
    public static ObjectReader reader(Class<?> type) {
        return OBJECT_MAPPER.reader(type);
    }

    public static SerializerProvider getSerializerProvider() {
        return OBJECT_MAPPER.getSerializerProvider();
    }

    public static ObjectMapper setConstructorDetector(ConstructorDetector cd) {
        return OBJECT_MAPPER.setConstructorDetector(cd);
    }

    public static JsonParser createParser(char[] content, int offset, int len) throws IOException {
        return OBJECT_MAPPER.createParser(content, offset, len);
    }

    public static ObjectMapper disable(JsonParser.Feature... features) {
        return OBJECT_MAPPER.disable(features);
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability, JsonTypeInfo.As includeAs) {
        return OBJECT_MAPPER.activateDefaultTyping(ptv, applicability, includeAs);
    }

    public static ObjectMapper registerModules(Module... modules) {
        return OBJECT_MAPPER.registerModules(modules);
    }

    public static ObjectMapper setDefaultSetterInfo(JsonSetter.Value v) {
        return OBJECT_MAPPER.setDefaultSetterInfo(v);
    }

    @Deprecated
    public static void setFilters(FilterProvider filterProvider) {
        OBJECT_MAPPER.setFilters(filterProvider);
    }

    public static ObjectMapper enable(DeserializationFeature first, DeserializationFeature... f) {
        return OBJECT_MAPPER.enable(first, f);
    }

    public static InjectableValues getInjectableValues() {
        return OBJECT_MAPPER.getInjectableValues();
    }

    public static ObjectMapper setFilterProvider(FilterProvider filterProvider) {
        return OBJECT_MAPPER.setFilterProvider(filterProvider);
    }

    public static <T> T treeToValue(TreeNode n, JavaType valueType) throws IllegalArgumentException, JsonProcessingException {
        return OBJECT_MAPPER.treeToValue(n, valueType);
    }

    public static ObjectMapper setSerializerFactory(SerializerFactory f) {
        return OBJECT_MAPPER.setSerializerFactory(f);
    }

    public static void writeValue(JsonGenerator g, Object value) throws IOException, StreamWriteException, DatabindException {
        OBJECT_MAPPER.writeValue(g, value);
    }

    public static ObjectReader reader(InjectableValues injectableValues) {
        return OBJECT_MAPPER.reader(injectableValues);
    }

    public static JsonGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        return OBJECT_MAPPER.createGenerator(out, enc);
    }

    public static ObjectMapper setAnnotationIntrospectors(AnnotationIntrospector serializerAI, AnnotationIntrospector deserializerAI) {
        return OBJECT_MAPPER.setAnnotationIntrospectors(serializerAI, deserializerAI);
    }

    public static ObjectWriter writer() {
        return OBJECT_MAPPER.writer();
    }

    public static JsonFactory tokenStreamFactory() {
        return OBJECT_MAPPER.tokenStreamFactory();
    }

    public static <T> T readValue(File src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static void writeValue(OutputStream out, Object value) throws IOException, StreamWriteException, DatabindException {
        OBJECT_MAPPER.writeValue(out, value);
    }

    public static ObjectWriter writer(CharacterEscapes escapes) {
        return OBJECT_MAPPER.writer(escapes);
    }

    public static ObjectMapper addHandler(DeserializationProblemHandler h) {
        return OBJECT_MAPPER.addHandler(h);
    }

    public static boolean isEnabled(JsonFactory.Feature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static <T extends TreeNode> T readTree(JsonParser p) throws IOException {
        return OBJECT_MAPPER.readTree(p);
    }

    public static void acceptJsonFormatVisitor(JavaType type, JsonFormatVisitorWrapper visitor) throws JsonMappingException {
        OBJECT_MAPPER.acceptJsonFormatVisitor(type, visitor);
    }

    public static ObjectMapper deactivateDefaultTyping() {
        return OBJECT_MAPPER.deactivateDefaultTyping();
    }

    public static JsonParser createParser(File src) throws IOException {
        return OBJECT_MAPPER.createParser(src);
    }

    public static DateFormat getDateFormat() {
        return OBJECT_MAPPER.getDateFormat();
    }

    public static JsonNode readTree(byte[] content) throws IOException {
        return OBJECT_MAPPER.readTree(content);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, ResolvedType valueType) throws IOException {
        return OBJECT_MAPPER.readValues(p, valueType);
    }

    public static SerializerProvider getSerializerProviderInstance() {
        return OBJECT_MAPPER.getSerializerProviderInstance();
    }

    public static void writeValue(DataOutput out, Object value) throws IOException {
        OBJECT_MAPPER.writeValue(out, value);
    }

    @Deprecated
    public static boolean canDeserialize(JavaType type, AtomicReference<Throwable> cause) {
        return OBJECT_MAPPER.canDeserialize(type, cause);
    }

    public static <T> T readValue(URL src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static JsonGenerator createGenerator(File outputFile, JsonEncoding enc) throws IOException {
        return OBJECT_MAPPER.createGenerator(outputFile, enc);
    }

    public static PropertyNamingStrategy getPropertyNamingStrategy() {
        return OBJECT_MAPPER.getPropertyNamingStrategy();
    }

    public static JavaType constructType(TypeReference<?> typeRef) {
        return OBJECT_MAPPER.constructType(typeRef);
    }

    public static ObjectMapper setLocale(Locale l) {
        return OBJECT_MAPPER.setLocale(l);
    }

    @Deprecated
    public static boolean canDeserialize(JavaType type) {
        return OBJECT_MAPPER.canDeserialize(type);
    }

    public static ObjectWriter writerFor(JavaType rootType) {
        return OBJECT_MAPPER.writerFor(rootType);
    }

    public static ObjectMapper configure(JsonGenerator.Feature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static ObjectReader reader(DeserializationFeature first, DeserializationFeature... other) {
        return OBJECT_MAPPER.reader(first, other);
    }

    public static JsonGenerator createGenerator(OutputStream out) throws IOException {
        return OBJECT_MAPPER.createGenerator(out);
    }

    public static JavaType constructType(Type t) {
        return OBJECT_MAPPER.constructType(t);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
        return OBJECT_MAPPER.readValues(p, valueTypeRef);
    }

    public static void writeTree(JsonGenerator g, TreeNode rootNode) throws IOException {
        OBJECT_MAPPER.writeTree(g, rootNode);
    }

    public static <T> T readValue(Reader src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectMapper setAnnotationIntrospector(AnnotationIntrospector ai) {
        return OBJECT_MAPPER.setAnnotationIntrospector(ai);
    }

    public static ObjectMapper setConfig(SerializationConfig config) {
        return OBJECT_MAPPER.setConfig(config);
    }

    public static <T> T convertValue(Object fromValue, JavaType toValueType) throws IllegalArgumentException {
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    public static ObjectMapper copyWith(JsonFactory factory) {
        return OBJECT_MAPPER.copyWith(factory);
    }

    public static ObjectReader readerFor(JavaType type) {
        return OBJECT_MAPPER.readerFor(type);
    }

    public static PolymorphicTypeValidator getPolymorphicTypeValidator() {
        return OBJECT_MAPPER.getPolymorphicTypeValidator();
    }

    public static ObjectMapper disable(JsonGenerator.Feature... features) {
        return OBJECT_MAPPER.disable(features);
    }

    public static <T> T readValue(String content, JavaType valueType) throws JsonProcessingException, JsonMappingException {
        return OBJECT_MAPPER.readValue(content, valueType);
    }

    public static SerializationConfig getSerializationConfig() {
        return OBJECT_MAPPER.getSerializationConfig();
    }

    public static <T> T readValue(byte[] src, int offset, int len, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, offset, len, valueTypeRef);
    }

    public static ObjectWriter writer(Base64Variant defaultBase64) {
        return OBJECT_MAPPER.writer(defaultBase64);
    }

    public static ObjectReader reader(FormatSchema schema) {
        return OBJECT_MAPPER.reader(schema);
    }

    public static ObjectWriter writerFor(TypeReference<?> rootType) {
        return OBJECT_MAPPER.writerFor(rootType);
    }

    public static Set<Object> getRegisteredModuleIds() {
        return OBJECT_MAPPER.getRegisteredModuleIds();
    }

    public static ObjectMapper setDefaultMergeable(Boolean b) {
        return OBJECT_MAPPER.setDefaultMergeable(b);
    }

    public static void writeValue(Writer w, Object value) throws IOException, StreamWriteException, DatabindException {
        OBJECT_MAPPER.writeValue(w, value);
    }

    public static JsonNode nullNode() {
        return OBJECT_MAPPER.nullNode();
    }

    public static <T> T readValue(InputStream src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static JsonGenerator createGenerator(DataOutput out) throws IOException {
        return OBJECT_MAPPER.createGenerator(out);
    }

    public static JsonGenerator createGenerator(Writer w) throws IOException {
        return OBJECT_MAPPER.createGenerator(w);
    }

    public static VisibilityChecker<?> getVisibilityChecker() {
        return OBJECT_MAPPER.getVisibilityChecker();
    }

    public static <T> T readValue(DataInput src, Class<T> valueType) throws IOException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsBytes(value);
    }

    public static JsonNode readTree(URL source) throws IOException {
        return OBJECT_MAPPER.readTree(source);
    }

    public static ObjectMapper setPropertyNamingStrategy(PropertyNamingStrategy s) {
        return OBJECT_MAPPER.setPropertyNamingStrategy(s);
    }

    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) throws IllegalArgumentException {
        return OBJECT_MAPPER.convertValue(fromValue, toValueTypeRef);
    }

    public static DeserializationConfig getDeserializationConfig() {
        return OBJECT_MAPPER.getDeserializationConfig();
    }

    public static ObjectMapper setDefaultAttributes(ContextAttributes attrs) {
        return OBJECT_MAPPER.setDefaultAttributes(attrs);
    }

    public static <T> T readValue(byte[] src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueTypeRef);
    }

    public static ObjectWriter writerWithDefaultPrettyPrinter() {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
    }

    public static ObjectMapper setSerializationInclusion(JsonInclude.Include incl) {
        return OBJECT_MAPPER.setSerializationInclusion(incl);
    }

    public static <T extends JsonNode> T valueToTree(Object fromValue) throws IllegalArgumentException {
        return OBJECT_MAPPER.valueToTree(fromValue);
    }

    public static MutableCoercionConfig coercionConfigFor(Class<?> physicalType) {
        return OBJECT_MAPPER.coercionConfigFor(physicalType);
    }

    public static JsonParser createParser(URL src) throws IOException {
        return OBJECT_MAPPER.createParser(src);
    }

    public static void registerSubtypes(Class<?>... classes) {
        OBJECT_MAPPER.registerSubtypes(classes);
    }

    public static <T> T readValue(String content, TypeReference<T> valueTypeRef) throws JsonProcessingException, JsonMappingException {
        return OBJECT_MAPPER.readValue(content, valueTypeRef);
    }

    @Deprecated
    public static boolean canSerialize(Class<?> type, AtomicReference<Throwable> cause) {
        return OBJECT_MAPPER.canSerialize(type, cause);
    }

    public static ObjectReader readerFor(TypeReference<?> typeRef) {
        return OBJECT_MAPPER.readerFor(typeRef);
    }

    public static ObjectMapper activateDefaultTypingAsProperty(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability, String propertyName) {
        return OBJECT_MAPPER.activateDefaultTypingAsProperty(ptv, applicability, propertyName);
    }

    @Deprecated
    public static boolean canSerialize(Class<?> type) {
        return OBJECT_MAPPER.canSerialize(type);
    }

    public static ObjectWriter writer(ContextAttributes attrs) {
        return OBJECT_MAPPER.writer(attrs);
    }

    public static ObjectMapper setVisibility(VisibilityChecker<?> vc) {
        return OBJECT_MAPPER.setVisibility(vc);
    }

    @Deprecated
    public static ObjectMapper disableDefaultTyping() {
        return OBJECT_MAPPER.disableDefaultTyping();
    }

    public static JsonParser treeAsTokens(TreeNode n) {
        return OBJECT_MAPPER.treeAsTokens(n);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, Class<T> valueType) throws IOException {
        return OBJECT_MAPPER.readValues(p, valueType);
    }

    public static <T> T readValue(Reader src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueTypeRef);
    }

    public static ObjectMapper setTimeZone(TimeZone tz) {
        return OBJECT_MAPPER.setTimeZone(tz);
    }

    @Deprecated
    public static void setMixInAnnotations(Map<Class<?>, Class<?>> sourceMixins) {
        OBJECT_MAPPER.setMixInAnnotations(sourceMixins);
    }

    public static ObjectReader readerWithView(Class<?> view) {
        return OBJECT_MAPPER.readerWithView(view);
    }

    public static Version version() {
        return OBJECT_MAPPER.version();
    }

    public static ObjectMapper clearProblemHandlers() {
        return OBJECT_MAPPER.clearProblemHandlers();
    }

    public static void acceptJsonFormatVisitor(Class<?> type, JsonFormatVisitorWrapper visitor) throws JsonMappingException {
        OBJECT_MAPPER.acceptJsonFormatVisitor(type, visitor);
    }

    public static boolean isEnabled(StreamReadFeature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static ObjectMapper setDefaultLeniency(Boolean b) {
        return OBJECT_MAPPER.setDefaultLeniency(b);
    }

    public static MutableCoercionConfig coercionConfigFor(LogicalType logicalType) {
        return OBJECT_MAPPER.coercionConfigFor(logicalType);
    }

    public static <T> T readValue(InputStream src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueTypeRef);
    }

    public static void writeValue(File resultFile, Object value) throws IOException, StreamWriteException, DatabindException {
        OBJECT_MAPPER.writeValue(resultFile, value);
    }

    public static ArrayNode createArrayNode() {
        return OBJECT_MAPPER.createArrayNode();
    }

    public static <T> T readValue(byte[] src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectReader readerFor(Class<?> type) {
        return OBJECT_MAPPER.readerFor(type);
    }

    public static JsonNode readTree(byte[] content, int offset, int len) throws IOException {
        return OBJECT_MAPPER.readTree(content, offset, len);
    }

    @Deprecated
    public static JsonSchema generateJsonSchema(Class<?> t) throws JsonMappingException {
        return OBJECT_MAPPER.generateJsonSchema(t);
    }

    public static ObjectWriter writer(PrettyPrinter pp) {
        return OBJECT_MAPPER.writer(pp);
    }

    public static JsonNodeFactory getNodeFactory() {
        return OBJECT_MAPPER.getNodeFactory();
    }

    public static <T> T readValue(JsonParser p, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(p, valueTypeRef);
    }

    public static <T> T readValue(byte[] src, int offset, int len, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, offset, len, valueType);
    }

    public static SerializerFactory getSerializerFactory() {
        return OBJECT_MAPPER.getSerializerFactory();
    }

    @Deprecated
    public static void addMixInAnnotations(Class<?> target, Class<?> mixinSource) {
        OBJECT_MAPPER.addMixInAnnotations(target, mixinSource);
    }

    public static ObjectMapper disable(DeserializationFeature feature) {
        return OBJECT_MAPPER.disable(feature);
    }

    public static <T> T readValue(InputStream src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectWriter writer(SerializationFeature first, SerializationFeature... other) {
        return OBJECT_MAPPER.writer(first, other);
    }

    public static ObjectMapper registerModule(Module module) {
        return OBJECT_MAPPER.registerModule(module);
    }

    public static JsonParser createParser(Reader r) throws IOException {
        return OBJECT_MAPPER.createParser(r);
    }

    public static void registerSubtypes(Collection<Class<?>> subtypes) {
        OBJECT_MAPPER.registerSubtypes(subtypes);
    }

    public static ObjectMapper enable(SerializationFeature first, SerializationFeature... f) {
        return OBJECT_MAPPER.enable(first, f);
    }

    public static JsonNode readTree(InputStream in) throws IOException {
        return OBJECT_MAPPER.readTree(in);
    }

    public static <T> T readValue(DataInput src, JavaType valueType) throws IOException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static ObjectMapper setConfig(DeserializationConfig config) {
        return OBJECT_MAPPER.setConfig(config);
    }

    public static JsonParser createNonBlockingByteArrayParser() throws IOException {
        return OBJECT_MAPPER.createNonBlockingByteArrayParser();
    }

    public static ObjectMapper setMixInResolver(ClassIntrospector.MixInResolver resolver) {
        return OBJECT_MAPPER.setMixInResolver(resolver);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping() {
        return OBJECT_MAPPER.enableDefaultTyping();
    }

    public static ObjectMapper configure(DatatypeFeature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static ObjectWriter writerWithView(Class<?> serializationView) {
        return OBJECT_MAPPER.writerWithView(serializationView);
    }

    public static ObjectMapper findAndRegisterModules() {
        return OBJECT_MAPPER.findAndRegisterModules();
    }

    @Deprecated
    public static ObjectMapper configure(MapperFeature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static ObjectMapper disable(SerializationFeature first, SerializationFeature... f) {
        return OBJECT_MAPPER.disable(first, f);
    }

    public static boolean isEnabled(DeserializationFeature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static <T> T updateValue(T valueToUpdate, Object overrides) throws JsonMappingException {
        return OBJECT_MAPPER.updateValue(valueToUpdate, overrides);
    }

    public static <T> T readValue(byte[] src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueType);
    }

    public static JsonParser createParser(char[] content) throws IOException {
        return OBJECT_MAPPER.createParser(content);
    }

    public static ObjectMapper setDefaultPrettyPrinter(PrettyPrinter pp) {
        return OBJECT_MAPPER.setDefaultPrettyPrinter(pp);
    }

    public static MutableConfigOverride configOverride(Class<?> type) {
        return OBJECT_MAPPER.configOverride(type);
    }

    public static <T> T readValue(JsonParser p, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(p, valueType);
    }

    public static ObjectReader readerForListOf(Class<?> type) {
        return OBJECT_MAPPER.readerForListOf(type);
    }

    public static ObjectMapper enable(JsonParser.Feature... features) {
        return OBJECT_MAPPER.enable(features);
    }

    public static JsonNode missingNode() {
        return OBJECT_MAPPER.missingNode();
    }

    public static ObjectMapper setSerializerProvider(DefaultSerializerProvider p) {
        return OBJECT_MAPPER.setSerializerProvider(p);
    }

    public static ObjectMapper setNodeFactory(JsonNodeFactory f) {
        return OBJECT_MAPPER.setNodeFactory(f);
    }

    public static ObjectMapper setInjectableValues(InjectableValues injectableValues) {
        return OBJECT_MAPPER.setInjectableValues(injectableValues);
    }

    public static ObjectMapper enable(DeserializationFeature feature) {
        return OBJECT_MAPPER.enable(feature);
    }

    public static ObjectMapper setDefaultPropertyInclusion(JsonInclude.Include incl) {
        return OBJECT_MAPPER.setDefaultPropertyInclusion(incl);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping applicability, String propertyName) {
        return OBJECT_MAPPER.enableDefaultTypingAsProperty(applicability, propertyName);
    }

    public static ObjectMapper configure(SerializationFeature f, boolean state) {
        return OBJECT_MAPPER.configure(f, state);
    }

    public static ObjectReader reader(ContextAttributes attrs) {
        return OBJECT_MAPPER.reader(attrs);
    }

    public static ObjectMapper setDefaultPropertyInclusion(JsonInclude.Value incl) {
        return OBJECT_MAPPER.setDefaultPropertyInclusion(incl);
    }

    public static ObjectMapper enable(SerializationFeature f) {
        return OBJECT_MAPPER.enable(f);
    }

    @Deprecated
    public static ObjectMapper setPropertyInclusion(JsonInclude.Value incl) {
        return OBJECT_MAPPER.setPropertyInclusion(incl);
    }

    public static ObjectWriter writer(SerializationFeature feature) {
        return OBJECT_MAPPER.writer(feature);
    }

    public static <T> T readValue(JsonParser p, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(p, valueType);
    }

    public static ObjectReader reader() {
        return OBJECT_MAPPER.reader();
    }

    public static TypeFactory getTypeFactory() {
        return OBJECT_MAPPER.getTypeFactory();
    }

    public static JsonParser createParser(InputStream in) throws IOException {
        return OBJECT_MAPPER.createParser(in);
    }

    public static ObjectMapper disable(SerializationFeature f) {
        return OBJECT_MAPPER.disable(f);
    }

    public static void registerSubtypes(NamedType... types) {
        OBJECT_MAPPER.registerSubtypes(types);
    }

    public static boolean isEnabled(MapperFeature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static JsonFactory getFactory() {
        return OBJECT_MAPPER.getFactory();
    }

    public static ObjectMapper setDefaultTyping(TypeResolverBuilder<?> typer) {
        return OBJECT_MAPPER.setDefaultTyping(typer);
    }

    public static <T> T readValue(File src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueTypeRef);
    }

    public static ObjectWriter writer(DateFormat df) {
        return OBJECT_MAPPER.writer(df);
    }

    public static JsonParser createParser(DataInput content) throws IOException {
        return OBJECT_MAPPER.createParser(content);
    }

    public static SubtypeResolver getSubtypeResolver() {
        return OBJECT_MAPPER.getSubtypeResolver();
    }

    public static boolean isEnabled(JsonParser.Feature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static ObjectMapper setMixIns(Map<Class<?>, Class<?>> sourceMixins) {
        return OBJECT_MAPPER.setMixIns(sourceMixins);
    }

    public static ObjectMapper setVisibility(PropertyAccessor forMethod, JsonAutoDetect.Visibility visibility) {
        return OBJECT_MAPPER.setVisibility(forMethod, visibility);
    }

    @Deprecated
    public static ObjectMapper enable(MapperFeature... f) {
        return OBJECT_MAPPER.enable(f);
    }

    public static boolean isEnabled(StreamWriteFeature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    @Deprecated
    public static ObjectWriter writerWithType(Class<?> rootType) {
        return OBJECT_MAPPER.writerWithType(rootType);
    }

    public static Object setHandlerInstantiator(HandlerInstantiator hi) {
        return OBJECT_MAPPER.setHandlerInstantiator(hi);
    }

    @Deprecated
    public static ObjectMapper disable(MapperFeature... f) {
        return OBJECT_MAPPER.disable(f);
    }

    public static JsonParser createParser(String content) throws IOException {
        return OBJECT_MAPPER.createParser(content);
    }

    public static boolean isEnabled(SerializationFeature f) {
        return OBJECT_MAPPER.isEnabled(f);
    }

    public static ObjectReader reader(Base64Variant defaultBase64) {
        return OBJECT_MAPPER.reader(defaultBase64);
    }

    @Deprecated
    public static ObjectWriter writerWithType(JavaType rootType) {
        return OBJECT_MAPPER.writerWithType(rootType);
    }

    public static ObjectMapper setAccessorNaming(AccessorNamingStrategy.Provider s) {
        return OBJECT_MAPPER.setAccessorNaming(s);
    }

    public static <T> T readValue(URL src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return OBJECT_MAPPER.readValue(src, valueTypeRef);
    }

    public static ObjectReader readerForArrayOf(Class<?> type) {
        return OBJECT_MAPPER.readerForArrayOf(type);
    }

    public static <T> T treeToValue(TreeNode n, Class<T> valueType) throws IllegalArgumentException, JsonProcessingException {
        return OBJECT_MAPPER.treeToValue(n, valueType);
    }

    @Deprecated
    public static ObjectWriter writerWithType(TypeReference<?> rootType) {
        return OBJECT_MAPPER.writerWithType(rootType);
    }
}
