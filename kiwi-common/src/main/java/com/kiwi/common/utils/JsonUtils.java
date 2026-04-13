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
@UtilityClass
public class JsonUtils {

    private static final ObjectMapper JSON = new ObjectMapper();


    public static ObjectMapper copy() {
        return JSON.copy();
    }

    @Deprecated
    public static ObjectWriter writerWithType(Class<?> rootType) {
        return JSON.writerWithType(rootType);
    }

    public static ObjectMapper activateDefaultTypingAsProperty(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability, String propertyName) {
        return JSON.activateDefaultTypingAsProperty(ptv, applicability, propertyName);
    }

    public static ObjectMapper setVisibility(VisibilityChecker<?> vc) {
        return JSON.setVisibility(vc);
    }

    public static <T extends JsonNode> T valueToTree(Object fromValue) throws IllegalArgumentException {
        return JSON.valueToTree(fromValue);
    }

    @Deprecated
    public static ObjectWriter writerWithType(TypeReference<?> rootType) {
        return JSON.writerWithType(rootType);
    }

    public static ObjectReader readerForArrayOf(Class<?> type) {
        return JSON.readerForArrayOf(type);
    }

    public static ObjectReader reader(Base64Variant defaultBase64) {
        return JSON.reader(defaultBase64);
    }

    public static JsonParser createParser(DataInput content) throws IOException {
        return JSON.createParser(content);
    }

    @Deprecated
    public static ObjectWriter writerWithType(JavaType rootType) {
        return JSON.writerWithType(rootType);
    }

    public static ObjectMapper setDefaultAttributes(ContextAttributes attrs) {
        return JSON.setDefaultAttributes(attrs);
    }

    public static <T> T readValue(URL src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueTypeRef);
    }

    public static ObjectReader reader() {
        return JSON.reader();
    }

    @Deprecated
    public static ObjectMapper disableDefaultTyping() {
        return JSON.disableDefaultTyping();
    }

    public static <T> T readValue(byte[] src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueTypeRef);
    }

    @Deprecated
    public static ObjectMapper setPropertyInclusion(JsonInclude.Value incl) {
        return JSON.setPropertyInclusion(incl);
    }

    public static boolean isEnabled(SerializationFeature f) {
        return JSON.isEnabled(f);
    }

    public static <T> T treeToValue(TreeNode n, Class<T> valueType) throws IllegalArgumentException, JsonProcessingException {
        return JSON.treeToValue(n, valueType);
    }

    public static ObjectMapper setDefaultPropertyInclusion(JsonInclude.Value incl) {
        return JSON.setDefaultPropertyInclusion(incl);
    }

    public static <T> T readValue(String content, TypeReference<T> valueTypeRef) throws JsonProcessingException, JsonMappingException {
        return JSON.readValue(content, valueTypeRef);
    }

    public static ObjectMapper setDefaultTyping(TypeResolverBuilder<?> typer) {
        return JSON.setDefaultTyping(typer);
    }

    @Deprecated
    public static ObjectMapper disable(MapperFeature... f) {
        return JSON.disable(f);
    }

    public static SubtypeResolver getSubtypeResolver() {
        return JSON.getSubtypeResolver();
    }

    public static JsonParser createParser(InputStream in) throws IOException {
        return JSON.createParser(in);
    }

    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) throws IllegalArgumentException {
        return JSON.convertValue(fromValue, toValueTypeRef);
    }

    @Deprecated
    public static ObjectMapper enable(MapperFeature... f) {
        return JSON.enable(f);
    }

    public static TypeFactory getTypeFactory() {
        return JSON.getTypeFactory();
    }

    public static void registerSubtypes(NamedType... types) {
        JSON.registerSubtypes(types);
    }

    public static Object setHandlerInstantiator(HandlerInstantiator hi) {
        return JSON.setHandlerInstantiator(hi);
    }

    public static boolean isEnabled(StreamWriteFeature f) {
        return JSON.isEnabled(f);
    }

    public static boolean isEnabled(StreamReadFeature f) {
        return JSON.isEnabled(f);
    }

    @Deprecated
    public static boolean canSerialize(Class<?> type, AtomicReference<Throwable> cause) {
        return JSON.canSerialize(type, cause);
    }

    public static ObjectReader readerFor(TypeReference<?> typeRef) {
        return JSON.readerFor(typeRef);
    }

    public static JsonParser createParser(URL src) throws IOException {
        return JSON.createParser(src);
    }

    public static SerializerFactory getSerializerFactory() {
        return JSON.getSerializerFactory();
    }

    @Deprecated
    public static void addMixInAnnotations(Class<?> target, Class<?> mixinSource) {
        JSON.addMixInAnnotations(target, mixinSource);
    }

    public static JsonNodeFactory getNodeFactory() {
        return JSON.getNodeFactory();
    }

    public static <T> T readValue(Reader src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueTypeRef);
    }

    public static ObjectWriter writerWithDefaultPrettyPrinter() {
        return JSON.writerWithDefaultPrettyPrinter();
    }

    public static ObjectMapper setTimeZone(TimeZone tz) {
        return JSON.setTimeZone(tz);
    }

    public static DeserializationConfig getDeserializationConfig() {
        return JSON.getDeserializationConfig();
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, Class<T> valueType) throws IOException {
        return JSON.readValues(p, valueType);
    }

    public static void writeValue(File resultFile, Object value) throws IOException, StreamWriteException, DatabindException {
        JSON.writeValue(resultFile, value);
    }

    public static void acceptJsonFormatVisitor(Class<?> type, JsonFormatVisitorWrapper visitor) throws JsonMappingException {
        JSON.acceptJsonFormatVisitor(type, visitor);
    }

    public static void registerSubtypes(Class<?>... classes) {
        JSON.registerSubtypes(classes);
    }

    public static MutableCoercionConfig coercionConfigFor(Class<?> physicalType) {
        return JSON.coercionConfigFor(physicalType);
    }

    public static JsonParser treeAsTokens(TreeNode n) {
        return JSON.treeAsTokens(n);
    }

    public static <T> T readValue(InputStream src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueTypeRef);
    }

    public static ObjectReader readerWithView(Class<?> view) {
        return JSON.readerWithView(view);
    }

    public static ObjectMapper setSerializationInclusion(JsonInclude.Include incl) {
        return JSON.setSerializationInclusion(incl);
    }

    public static ObjectWriter writer(ContextAttributes attrs) {
        return JSON.writer(attrs);
    }

    @Deprecated
    public static boolean canSerialize(Class<?> type) {
        return JSON.canSerialize(type);
    }

    public static ObjectMapper setDefaultPrettyPrinter(PrettyPrinter pp) {
        return JSON.setDefaultPrettyPrinter(pp);
    }

    public static MutableConfigOverride configOverride(Class<?> type) {
        return JSON.configOverride(type);
    }

    public static <T> T readValue(Reader src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    @Deprecated
    public static ObjectReader reader(JavaType type) {
        return JSON.reader(type);
    }

    public static ObjectMapper configure(DatatypeFeature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static ObjectMapper disable(SerializationFeature first, SerializationFeature... f) {
        return JSON.disable(first, f);
    }

    public static ObjectMapper addMixIn(Class<?> target, Class<?> mixinSource) {
        return JSON.addMixIn(target, mixinSource);
    }

    @Deprecated
    public static ObjectMapper configure(MapperFeature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static ObjectMapper setDefaultPropertyInclusion(JsonInclude.Include incl) {
        return JSON.setDefaultPropertyInclusion(incl);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping applicability, String propertyName) {
        return JSON.enableDefaultTypingAsProperty(applicability, propertyName);
    }

    public static JsonNode readTree(InputStream in) throws IOException {
        return JSON.readTree(in);
    }

    @Deprecated
    public static ObjectReader reader(TypeReference<?> type) {
        return JSON.reader(type);
    }

    public static ObjectMapper setSerializerProvider(DefaultSerializerProvider p) {
        return JSON.setSerializerProvider(p);
    }

    public static ObjectMapper disable(DeserializationFeature feature) {
        return JSON.disable(feature);
    }

    public static ObjectReader readerForMapOf(Class<?> type) {
        return JSON.readerForMapOf(type);
    }

    public static ObjectMapper setNodeFactory(JsonNodeFactory f) {
        return JSON.setNodeFactory(f);
    }

    public static JsonParser createParser(char[] content) throws IOException {
        return JSON.createParser(content);
    }

    public static ObjectMapper enable(SerializationFeature first, SerializationFeature... f) {
        return JSON.enable(first, f);
    }

    @Deprecated
    public static ObjectReader reader(Class<?> type) {
        return JSON.reader(type);
    }

    @Deprecated
    public static void setFilters(FilterProvider filterProvider) {
        JSON.setFilters(filterProvider);
    }

    public static JsonParser createParser(byte[] content) throws IOException {
        return JSON.createParser(content);
    }

    public static DeserializationContext getDeserializationContext() {
        return JSON.getDeserializationContext();
    }

    public static <T> T readValue(DataInput src, JavaType valueType) throws IOException {
        return JSON.readValue(src, valueType);
    }

    public static <T> T readValue(InputStream src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectMapper setFilterProvider(FilterProvider filterProvider) {
        return JSON.setFilterProvider(filterProvider);
    }

    public static ObjectMapper disable(JsonParser.Feature... features) {
        return JSON.disable(features);
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv) {
        return JSON.activateDefaultTyping(ptv);
    }

    public static InjectableValues getInjectableValues() {
        return JSON.getInjectableValues();
    }

    public static ObjectMapper enable(DeserializationFeature first, DeserializationFeature... f) {
        return JSON.enable(first, f);
    }

    public static ObjectWriter writer(SerializationFeature first, SerializationFeature... other) {
        return JSON.writer(first, other);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, JavaType valueType) throws IOException {
        return JSON.readValues(p, valueType);
    }

    public static ObjectMapper configure(JsonParser.Feature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static Class<?> findMixInClassFor(Class<?> cls) {
        return JSON.findMixInClassFor(cls);
    }

    public static ObjectMapper configure(DeserializationFeature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static ObjectWriter writerWithView(Class<?> serializationView) {
        return JSON.writerWithView(serializationView);
    }

    public static boolean isEnabled(JsonParser.Feature f) {
        return JSON.isEnabled(f);
    }

    public static ObjectMapper setMixIns(Map<Class<?>, Class<?>> sourceMixins) {
        return JSON.setMixIns(sourceMixins);
    }

    @Deprecated
    public static JsonFactory getJsonFactory() {
        return JSON.getJsonFactory();
    }

    public static ObjectMapper setVisibility(PropertyAccessor forMethod, JsonAutoDetect.Visibility visibility) {
        return JSON.setVisibility(forMethod, visibility);
    }

    public static ObjectMapper disable(SerializationFeature f) {
        return JSON.disable(f);
    }

    public static <T> T readValue(byte[] src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static JsonFactory getFactory() {
        return JSON.getFactory();
    }

    public static ObjectReader reader(ContextAttributes attrs) {
        return JSON.reader(attrs);
    }

    public static ObjectReader readerForListOf(Class<?> type) {
        return JSON.readerForListOf(type);
    }

    public static JsonParser createParser(String content) throws IOException {
        return JSON.createParser(content);
    }

    public static boolean isEnabled(MapperFeature f) {
        return JSON.isEnabled(f);
    }

    public static <T> T readValue(JsonParser p, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(p, valueType);
    }

    public static ObjectMapper enable(SerializationFeature f) {
        return JSON.enable(f);
    }

    public static <T> T updateValue(T valueToUpdate, Object overrides) throws JsonMappingException {
        return JSON.updateValue(valueToUpdate, overrides);
    }

    public static ObjectMapper setAccessorNaming(AccessorNamingStrategy.Provider s) {
        return JSON.setAccessorNaming(s);
    }

    public static ObjectWriter writer(SerializationFeature feature) {
        return JSON.writer(feature);
    }

    public static ObjectMapper findAndRegisterModules() {
        return JSON.findAndRegisterModules();
    }

    public static void registerSubtypes(Collection<Class<?>> subtypes) {
        JSON.registerSubtypes(subtypes);
    }

    public static ObjectMapper enable(DeserializationFeature feature) {
        return JSON.enable(feature);
    }

    public static ObjectMapper configure(SerializationFeature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static <T> T readValue(File src, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueTypeRef);
    }

    public static ObjectWriter writer(DateFormat df) {
        return JSON.writer(df);
    }

    public static ObjectMapper enable(JsonParser.Feature... features) {
        return JSON.enable(features);
    }

    public static ObjectMapper registerModule(Module module) {
        return JSON.registerModule(module);
    }

    public static ObjectMapper setMixInResolver(ClassIntrospector.MixInResolver resolver) {
        return JSON.setMixInResolver(resolver);
    }

    public static ObjectMapper setInjectableValues(InjectableValues injectableValues) {
        return JSON.setInjectableValues(injectableValues);
    }

    public static <T> T readValue(JsonParser p, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(p, valueType);
    }

    public static JsonParser createNonBlockingByteArrayParser() throws IOException {
        return JSON.createNonBlockingByteArrayParser();
    }

    public static ObjectMapper setConfig(DeserializationConfig config) {
        return JSON.setConfig(config);
    }

    public static boolean isEnabled(DeserializationFeature f) {
        return JSON.isEnabled(f);
    }

    public static JsonNode missingNode() {
        return JSON.missingNode();
    }

    public static JsonParser createParser(Reader r) throws IOException {
        return JSON.createParser(r);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping() {
        return JSON.enableDefaultTyping();
    }

    public static ObjectMapper setDateFormat(DateFormat dateFormat) {
        return JSON.setDateFormat(dateFormat);
    }

    public static JsonNode readTree(String content) throws JsonProcessingException, JsonMappingException {
        return JSON.readTree(content);
    }

    public static ObjectMapper registerModules(Iterable<? extends Module> modules) {
        return JSON.registerModules(modules);
    }

    public static void writeTree(JsonGenerator g, JsonNode rootNode) throws IOException {
        JSON.writeTree(g, rootNode);
    }

    public static void writeValue(Writer w, Object value) throws IOException, StreamWriteException, DatabindException {
        JSON.writeValue(w, value);
    }

    public static ObjectWriter writerFor(TypeReference<?> rootType) {
        return JSON.writerFor(rootType);
    }

    public static ObjectMapper setCacheProvider(CacheProvider cacheProvider) {
        return JSON.setCacheProvider(cacheProvider);
    }

    public static <T> T readValue(DataInput src, Class<T> valueType) throws IOException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability) {
        return JSON.activateDefaultTyping(ptv, applicability);
    }

    public static ObjectMapper setDefaultVisibility(JsonAutoDetect.Value vis) {
        return JSON.setDefaultVisibility(vis);
    }

    public static ObjectMapper setBase64Variant(Base64Variant v) {
        return JSON.setBase64Variant(v);
    }

    public static ObjectMapper enable(JsonGenerator.Feature... features) {
        return JSON.enable(features);
    }

    public static <T> T readValue(JsonParser p, ResolvedType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(p, valueType);
    }

    public static byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        return JSON.writeValueAsBytes(value);
    }

    public static <T> T readValue(Reader src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectReader reader(JsonNodeFactory nodeFactory) {
        return JSON.reader(nodeFactory);
    }

    public static <T> T readValue(File src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectReader reader(DeserializationFeature feature) {
        return JSON.reader(feature);
    }

    public static ObjectMapper configure(JsonGenerator.Feature f, boolean state) {
        return JSON.configure(f, state);
    }

    public static JsonNode readTree(URL source) throws IOException {
        return JSON.readTree(source);
    }

    public static ObjectWriter writer(FormatSchema schema) {
        return JSON.writer(schema);
    }

    public static ObjectMapper setPropertyNamingStrategy(PropertyNamingStrategy s) {
        return JSON.setPropertyNamingStrategy(s);
    }

    public static JavaType constructType(Type t) {
        return JSON.constructType(t);
    }

    public static ObjectReader readerForUpdating(Object valueToUpdate) {
        return JSON.readerForUpdating(valueToUpdate);
    }

    public static PolymorphicTypeValidator getPolymorphicTypeValidator() {
        return JSON.getPolymorphicTypeValidator();
    }

    public static JsonNode nullNode() {
        return JSON.nullNode();
    }

    public static ObjectMapper copyWith(JsonFactory factory) {
        return JSON.copyWith(factory);
    }

    public static SerializationConfig getSerializationConfig() {
        return JSON.getSerializationConfig();
    }

    public static <T> T readValue(InputStream src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static JsonGenerator createGenerator(OutputStream out) throws IOException {
        return JSON.createGenerator(out);
    }

    public static ObjectMapper setAnnotationIntrospector(AnnotationIntrospector ai) {
        return JSON.setAnnotationIntrospector(ai);
    }

    public static JsonParser createParser(char[] content, int offset, int len) throws IOException {
        return JSON.createParser(content, offset, len);
    }

    @Deprecated
    public static void setVisibilityChecker(VisibilityChecker<?> vc) {
        JSON.setVisibilityChecker(vc);
    }

    public static ObjectMapper setConstructorDetector(ConstructorDetector cd) {
        return JSON.setConstructorDetector(cd);
    }

    public static ObjectNode createObjectNode() {
        return JSON.createObjectNode();
    }

    public static <T> T readValue(URL src, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectWriter writerFor(Class<?> rootType) {
        return JSON.writerFor(rootType);
    }

    public static <T> T readValue(byte[] src, int offset, int len, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, offset, len, valueType);
    }

    public static ObjectMapper registerModules(Module... modules) {
        return JSON.registerModules(modules);
    }

    public static ObjectMapper setPolymorphicTypeValidator(PolymorphicTypeValidator ptv) {
        return JSON.setPolymorphicTypeValidator(ptv);
    }

    public static MutableCoercionConfig coercionConfigDefaults() {
        return JSON.coercionConfigDefaults();
    }

    public static JsonNode readTree(Reader r) throws IOException {
        return JSON.readTree(r);
    }

    public static <T> T treeToValue(TreeNode n, TypeReference<T> toValueTypeRef) throws IllegalArgumentException, JsonProcessingException {
        return JSON.treeToValue(n, toValueTypeRef);
    }

    public static String writeValueAsString(Object value) throws JsonProcessingException {
        return JSON.writeValueAsString(value);
    }

    public static SerializerProvider getSerializerProvider() {
        return JSON.getSerializerProvider();
    }

    public static ObjectMapper setDefaultSetterInfo(JsonSetter.Value v) {
        return JSON.setDefaultSetterInfo(v);
    }

    public static ObjectMapper activateDefaultTyping(PolymorphicTypeValidator ptv, ObjectMapper.DefaultTyping applicability, JsonTypeInfo.As includeAs) {
        return JSON.activateDefaultTyping(ptv, applicability, includeAs);
    }

    public static ObjectMapper disable(DeserializationFeature first, DeserializationFeature... f) {
        return JSON.disable(first, f);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping(ObjectMapper.DefaultTyping dti) {
        return JSON.enableDefaultTyping(dti);
    }

    public static ObjectMapper setTypeFactory(TypeFactory f) {
        return JSON.setTypeFactory(f);
    }

    public static ObjectWriter writer(FilterProvider filterProvider) {
        return JSON.writer(filterProvider);
    }

    public static int mixInCount() {
        return JSON.mixInCount();
    }

    public static <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException, JsonMappingException {
        return JSON.readValue(content, valueType);
    }

    public static JsonParser createParser(byte[] content, int offset, int len) throws IOException {
        return JSON.createParser(content, offset, len);
    }

    @Deprecated
    public static ObjectMapper enableDefaultTyping(ObjectMapper.DefaultTyping applicability, JsonTypeInfo.As includeAs) {
        return JSON.enableDefaultTyping(applicability, includeAs);
    }

    public static boolean isEnabled(JsonGenerator.Feature f) {
        return JSON.isEnabled(f);
    }

    public static JsonNode readTree(File file) throws IOException {
        return JSON.readTree(file);
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) throws IllegalArgumentException {
        return JSON.convertValue(fromValue, toValueType);
    }

    public static ObjectMapper setSubtypeResolver(SubtypeResolver str) {
        return JSON.setSubtypeResolver(str);
    }

    public static SerializerProvider getSerializerProviderInstance() {
        return JSON.getSerializerProviderInstance();
    }

    public static ObjectReader readerFor(Class<?> type) {
        return JSON.readerFor(type);
    }

    public static JsonGenerator createGenerator(File outputFile, JsonEncoding enc) throws IOException {
        return JSON.createGenerator(outputFile, enc);
    }

    public static ObjectMapper setDefaultLeniency(Boolean b) {
        return JSON.setDefaultLeniency(b);
    }

    public static MutableCoercionConfig coercionConfigFor(LogicalType logicalType) {
        return JSON.coercionConfigFor(logicalType);
    }

    public static JsonFactory tokenStreamFactory() {
        return JSON.tokenStreamFactory();
    }

    public static boolean isEnabled(JsonFactory.Feature f) {
        return JSON.isEnabled(f);
    }

    public static <T extends TreeNode> T readTree(JsonParser p) throws IOException {
        return JSON.readTree(p);
    }

    public static ObjectWriter writer(PrettyPrinter pp) {
        return JSON.writer(pp);
    }

    @Deprecated
    public static JsonSchema generateJsonSchema(Class<?> t) throws JsonMappingException {
        return JSON.generateJsonSchema(t);
    }

    public static JsonParser createParser(File src) throws IOException {
        return JSON.createParser(src);
    }

    public static PropertyNamingStrategy getPropertyNamingStrategy() {
        return JSON.getPropertyNamingStrategy();
    }

    public static JavaType constructType(TypeReference<?> typeRef) {
        return JSON.constructType(typeRef);
    }

    public static <T> T readValue(byte[] src, int offset, int len, Class<T> valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, offset, len, valueType);
    }

    public static void writeValue(JsonGenerator g, Object value) throws IOException, StreamWriteException, DatabindException {
        JSON.writeValue(g, value);
    }

    public static <T> T treeToValue(TreeNode n, JavaType valueType) throws IllegalArgumentException, JsonProcessingException {
        return JSON.treeToValue(n, valueType);
    }

    public static void acceptJsonFormatVisitor(JavaType type, JsonFormatVisitorWrapper visitor) throws JsonMappingException {
        JSON.acceptJsonFormatVisitor(type, visitor);
    }

    @Deprecated
    public static void setMixInAnnotations(Map<Class<?>, Class<?>> sourceMixins) {
        JSON.setMixInAnnotations(sourceMixins);
    }

    public static <T> T readValue(JsonParser p, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(p, valueTypeRef);
    }

    public static void writeValue(OutputStream out, Object value) throws IOException, StreamWriteException, DatabindException {
        JSON.writeValue(out, value);
    }

    public static Version version() {
        return JSON.version();
    }

    public static JsonNode readTree(byte[] content, int offset, int len) throws IOException {
        return JSON.readTree(content, offset, len);
    }

    public static <T> T readValue(File src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectReader reader(InjectableValues injectableValues) {
        return JSON.reader(injectableValues);
    }

    public static ArrayNode createArrayNode() {
        return JSON.createArrayNode();
    }

    public static <T> T readValue(byte[] src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectWriter writer(CharacterEscapes escapes) {
        return JSON.writer(escapes);
    }

    public static ObjectMapper clearProblemHandlers() {
        return JSON.clearProblemHandlers();
    }

    public static ObjectMapper setDefaultMergeable(Boolean b) {
        return JSON.setDefaultMergeable(b);
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, ResolvedType valueType) throws IOException {
        return JSON.readValues(p, valueType);
    }

    public static ObjectMapper deactivateDefaultTyping() {
        return JSON.deactivateDefaultTyping();
    }

    public static JsonGenerator createGenerator(Writer w) throws IOException {
        return JSON.createGenerator(w);
    }

    public static ObjectMapper setConfig(SerializationConfig config) {
        return JSON.setConfig(config);
    }

    @Deprecated
    public static boolean canDeserialize(JavaType type) {
        return JSON.canDeserialize(type);
    }

    public static ObjectMapper disable(JsonGenerator.Feature... features) {
        return JSON.disable(features);
    }

    public static ObjectWriter writerFor(JavaType rootType) {
        return JSON.writerFor(rootType);
    }

    public static void writeTree(JsonGenerator g, TreeNode rootNode) throws IOException {
        JSON.writeTree(g, rootNode);
    }

    @Deprecated
    public static boolean canDeserialize(JavaType type, AtomicReference<Throwable> cause) {
        return JSON.canDeserialize(type, cause);
    }

    public static <T> T readValue(URL src, JavaType valueType) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, valueType);
    }

    public static ObjectWriter writer() {
        return JSON.writer();
    }

    public static <T> MappingIterator<T> readValues(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
        return JSON.readValues(p, valueTypeRef);
    }

    public static Set<Object> getRegisteredModuleIds() {
        return JSON.getRegisteredModuleIds();
    }

    public static JsonGenerator createGenerator(DataOutput out) throws IOException {
        return JSON.createGenerator(out);
    }

    public static VisibilityChecker<?> getVisibilityChecker() {
        return JSON.getVisibilityChecker();
    }

    public static ObjectMapper setLocale(Locale l) {
        return JSON.setLocale(l);
    }

    public static ObjectReader reader(FormatSchema schema) {
        return JSON.reader(schema);
    }

    public static ObjectMapper setAnnotationIntrospectors(AnnotationIntrospector serializerAI, AnnotationIntrospector deserializerAI) {
        return JSON.setAnnotationIntrospectors(serializerAI, deserializerAI);
    }

    public static ObjectMapper setSerializerFactory(SerializerFactory f) {
        return JSON.setSerializerFactory(f);
    }

    public static <T> T readValue(byte[] src, int offset, int len, TypeReference<T> valueTypeRef) throws IOException, StreamReadException, DatabindException {
        return JSON.readValue(src, offset, len, valueTypeRef);
    }

    public static ObjectReader reader(DeserializationFeature first, DeserializationFeature... other) {
        return JSON.reader(first, other);
    }

    public static <T> T convertValue(Object fromValue, JavaType toValueType) throws IllegalArgumentException {
        return JSON.convertValue(fromValue, toValueType);
    }

    public static ObjectMapper addHandler(DeserializationProblemHandler h) {
        return JSON.addHandler(h);
    }

    public static JsonGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        return JSON.createGenerator(out, enc);
    }

    public static ObjectWriter writer(Base64Variant defaultBase64) {
        return JSON.writer(defaultBase64);
    }

    public static DateFormat getDateFormat() {
        return JSON.getDateFormat();
    }

    public static void writeValue(DataOutput out, Object value) throws IOException {
        JSON.writeValue(out, value);
    }

    public static <T> T readValue(String content, JavaType valueType) throws JsonProcessingException, JsonMappingException {
        return JSON.readValue(content, valueType);
    }

    public static ObjectReader readerFor(JavaType type) {
        return JSON.readerFor(type);
    }

    public static JsonNode readTree(byte[] content) throws IOException {
        return JSON.readTree(content);
    }
}
