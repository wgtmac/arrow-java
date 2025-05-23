/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.arrow.adapter.avro;

import static org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE;
import static org.apache.arrow.vector.types.FloatingPointPrecision.SINGLE;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.arrow.adapter.avro.consumers.AvroArraysConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroBooleanConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroBytesConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroDoubleConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroEnumConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroFixedConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroFloatConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroIntConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroLongConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroMapConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroNullConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroNullableConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroStringConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroStructConsumer;
import org.apache.arrow.adapter.avro.consumers.AvroUnionsConsumer;
import org.apache.arrow.adapter.avro.consumers.CompositeAvroConsumer;
import org.apache.arrow.adapter.avro.consumers.Consumer;
import org.apache.arrow.adapter.avro.consumers.SkipConsumer;
import org.apache.arrow.adapter.avro.consumers.SkipFunction;
import org.apache.arrow.adapter.avro.consumers.logical.AvroDateConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroDecimal256Consumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroDecimalConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimeMicroConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimeMillisConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampMicrosConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampMicrosTzConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampMillisConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampMillisTzConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampNanosConsumer;
import org.apache.arrow.adapter.avro.consumers.logical.AvroTimestampNanosTzConsumer;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.util.Preconditions;
import org.apache.arrow.vector.BaseIntVector;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.Decimal256Vector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.NullVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.TimeStampMilliVector;
import org.apache.arrow.vector.TimeStampNanoTZVector;
import org.apache.arrow.vector.TimeStampNanoVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.MapVector;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.complex.UnionVector;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.types.UnionMode;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.util.JsonStringArrayList;
import org.apache.arrow.vector.util.ValueVectorUtility;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;

/**
 * Class that does most of the work to convert Avro data into Arrow columnar format Vector objects.
 */
public class AvroToArrowUtils {

  /**
   * Creates a {@link Consumer} from the {@link Schema}
   *
   * <p>This method currently performs following type mapping for Avro data types to corresponding
   * Arrow data types.
   *
   * <ul>
   *   <li>STRING --> ArrowType.Utf8
   *   <li>INT --> ArrowType.Int(32, signed)
   *   <li>LONG --> ArrowType.Int(64, signed)
   *   <li>FLOAT --> ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)
   *   <li>DOUBLE --> ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)
   *   <li>BOOLEAN --> ArrowType.Bool
   *   <li>BYTES --> ArrowType.Binary
   *   <li>ARRAY --> ArrowType.List
   *   <li>MAP --> ArrowType.Map
   *   <li>FIXED --> ArrowType.FixedSizeBinary
   *   <li>RECORD --> ArrowType.Struct
   *   <li>UNION --> ArrowType.Union
   *   <li>ENUM--> ArrowType.Int
   *   <li>DECIMAL --> ArrowType.Decimal
   *   <li>Date --> ArrowType.Date(DateUnit.DAY)
   *   <li>TimeMillis --> ArrowType.Time(TimeUnit.MILLISECOND, 32)
   *   <li>TimeMicros --> ArrowType.Time(TimeUnit.MICROSECOND, 64)
   *   <li>TimestampMillis --> ArrowType.Timestamp(TimeUnit.MILLISECOND, null)
   *   <li>TimestampMicros --> ArrowType.Timestamp(TimeUnit.MICROSECOND, null)
   * </ul>
   */
  private static Consumer createConsumer(Schema schema, String name, AvroToArrowConfig config) {
    return createConsumer(schema, name, false, config, null);
  }

  private static Consumer createConsumer(
      Schema schema, String name, AvroToArrowConfig config, FieldVector vector) {
    return createConsumer(schema, name, false, config, vector);
  }

  /**
   * Create a consumer with the given Avro schema.
   *
   * @param schema avro schema
   * @param name arrow field name
   * @param consumerVector vector to keep in consumer, if v == null, will create a new vector via
   *     field.
   * @return consumer
   */
  private static Consumer createConsumer(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      FieldVector consumerVector) {

    Preconditions.checkNotNull(schema, "Avro schema object can't be null");
    Preconditions.checkNotNull(config, "Config can't be null");

    final BufferAllocator allocator = config.getAllocator();

    final Schema.Type type = schema.getType();
    final LogicalType logicalType = schema.getLogicalType();

    final ArrowType arrowType;
    final FieldType fieldType;
    final FieldVector vector;
    final Consumer consumer;

    switch (type) {
      case UNION:
        boolean nullableUnion =
            schema.getTypes().stream().anyMatch(t -> t.getType() == Schema.Type.NULL);
        if (schema.getTypes().size() == 2 && nullableUnion && !config.isLegacyMode()) {
          // For a simple nullable (null | type), interpret the union as a single nullable field.
          // Not available in legacy mode, which uses the literal interpretation instead
          int nullIndex = schema.getTypes().get(0).getType() == Schema.Type.NULL ? 0 : 1;
          int childIndex = nullIndex == 0 ? 1 : 0;
          Schema childSchema = schema.getTypes().get(childIndex);
          Consumer<?> childConsumer =
              createConsumer(childSchema, name, true, config, consumerVector);
          consumer = new AvroNullableConsumer<>(childConsumer, nullIndex);
        } else {
          // Literal interpretation of a union, which may or may not include a null element.
          consumer = createUnionConsumer(schema, name, nullableUnion, config, consumerVector);
        }
        break;
      case ARRAY:
        consumer = createArrayConsumer(schema, name, nullable, config, consumerVector);
        break;
      case MAP:
        consumer = createMapConsumer(schema, name, nullable, config, consumerVector);
        break;
      case RECORD:
        consumer = createStructConsumer(schema, name, nullable, config, consumerVector);
        break;
      case ENUM:
        consumer = createEnumConsumer(schema, name, nullable, config, consumerVector);
        break;
      case STRING:
        arrowType = new ArrowType.Utf8();
        fieldType =
            new FieldType(nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
        vector = createVector(consumerVector, fieldType, name, allocator);
        consumer = new AvroStringConsumer((VarCharVector) vector);
        break;
      case FIXED:
        Map<String, String> extProps = createExternalProps(schema, config);
        if (logicalType instanceof LogicalTypes.Decimal) {
          arrowType = createDecimalArrowType((LogicalTypes.Decimal) logicalType, schema);
          fieldType =
              new FieldType(
                  nullable,
                  arrowType,
                  /* dictionary= */ null,
                  getMetaData(schema, extProps, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          if (schema.getFixedSize() <= 16) {
            consumer =
                new AvroDecimalConsumer.FixedDecimalConsumer(
                    (DecimalVector) vector, schema.getFixedSize());
          } else {
            consumer =
                new AvroDecimal256Consumer.FixedDecimal256Consumer(
                    (Decimal256Vector) vector, schema.getFixedSize());
          }
        } else {
          arrowType = new ArrowType.FixedSizeBinary(schema.getFixedSize());
          fieldType =
              new FieldType(
                  nullable,
                  arrowType,
                  /* dictionary= */ null,
                  getMetaData(schema, extProps, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroFixedConsumer((FixedSizeBinaryVector) vector, schema.getFixedSize());
        }
        break;
      case INT:
        if (logicalType instanceof LogicalTypes.Date) {
          arrowType = new ArrowType.Date(DateUnit.DAY);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroDateConsumer((DateDayVector) vector);
        } else if (logicalType instanceof LogicalTypes.TimeMillis) {
          arrowType = new ArrowType.Time(TimeUnit.MILLISECOND, 32);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimeMillisConsumer((TimeMilliVector) vector);
        } else {
          arrowType = new ArrowType.Int(32, /* isSigned= */ true);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroIntConsumer((IntVector) vector);
        }
        break;
      case BOOLEAN:
        arrowType = new ArrowType.Bool();
        fieldType =
            new FieldType(nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
        vector = createVector(consumerVector, fieldType, name, allocator);
        consumer = new AvroBooleanConsumer((BitVector) vector);
        break;
      case LONG:
        if (logicalType instanceof LogicalTypes.TimeMicros) {
          arrowType = new ArrowType.Time(TimeUnit.MICROSECOND, 64);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimeMicroConsumer((TimeMicroVector) vector);
        } else if (logicalType instanceof LogicalTypes.TimestampMillis && !config.isLegacyMode()) {
          // In legacy mode the timestamp-xxx types are treated as local, there is no zone aware
          // type
          arrowType = new ArrowType.Timestamp(TimeUnit.MILLISECOND, "UTC");
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampMillisTzConsumer((TimeStampMilliTZVector) vector);
        } else if (logicalType instanceof LogicalTypes.TimestampMicros && !config.isLegacyMode()) {
          arrowType = new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC");
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampMicrosTzConsumer((TimeStampMicroTZVector) vector);
        } else if (logicalType instanceof LogicalTypes.TimestampNanos && !config.isLegacyMode()) {
          arrowType = new ArrowType.Timestamp(TimeUnit.NANOSECOND, "UTC");
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampNanosTzConsumer((TimeStampNanoTZVector) vector);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampMillis
            || (logicalType instanceof LogicalTypes.TimestampMillis && config.isLegacyMode())) {
          arrowType = new ArrowType.Timestamp(TimeUnit.MILLISECOND, null);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampMillisConsumer((TimeStampMilliVector) vector);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampMicros
            || (logicalType instanceof LogicalTypes.TimestampMicros && config.isLegacyMode())) {
          // In legacy mode the timestamp-xxx types are treated as local
          arrowType = new ArrowType.Timestamp(TimeUnit.MICROSECOND, null);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampMicrosConsumer((TimeStampMicroVector) vector);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampNanos
            || (logicalType instanceof LogicalTypes.TimestampNanos && config.isLegacyMode())) {
          arrowType = new ArrowType.Timestamp(TimeUnit.NANOSECOND, null);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroTimestampNanosConsumer((TimeStampNanoVector) vector);
        } else {
          arrowType = new ArrowType.Int(64, /* isSigned= */ true);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroLongConsumer((BigIntVector) vector);
        }
        break;
      case FLOAT:
        arrowType = new ArrowType.FloatingPoint(SINGLE);
        fieldType =
            new FieldType(nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
        vector = createVector(consumerVector, fieldType, name, allocator);
        consumer = new AvroFloatConsumer((Float4Vector) vector);
        break;
      case DOUBLE:
        arrowType = new ArrowType.FloatingPoint(DOUBLE);
        fieldType =
            new FieldType(nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
        vector = createVector(consumerVector, fieldType, name, allocator);
        consumer = new AvroDoubleConsumer((Float8Vector) vector);
        break;
      case BYTES:
        if (logicalType instanceof LogicalTypes.Decimal) {
          LogicalTypes.Decimal decimalType = (LogicalTypes.Decimal) logicalType;
          arrowType = createDecimalArrowType(decimalType, schema);
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          if (decimalType.getPrecision() <= 38) {
            consumer = new AvroDecimalConsumer.BytesDecimalConsumer((DecimalVector) vector);
          } else {
            consumer =
                new AvroDecimal256Consumer.BytesDecimal256Consumer((Decimal256Vector) vector);
          }
        } else {
          arrowType = new ArrowType.Binary();
          fieldType =
              new FieldType(
                  nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
          vector = createVector(consumerVector, fieldType, name, allocator);
          consumer = new AvroBytesConsumer((VarBinaryVector) vector);
        }
        break;
      case NULL:
        arrowType = new ArrowType.Null();
        fieldType =
            new FieldType(nullable, arrowType, /* dictionary= */ null, getMetaData(schema, config));
        vector = new NullVector(name, fieldType); // Respect nullability defined in fieldType
        consumer = new AvroNullConsumer((NullVector) vector);
        break;
      default:
        // no-op, shouldn't get here
        throw new UnsupportedOperationException(
            "Can't convert avro type %s to arrow type." + type.getName());
    }
    return consumer;
  }

  private static ArrowType createDecimalArrowType(LogicalTypes.Decimal logicalType, Schema schema) {
    final int scale = logicalType.getScale();
    final int precision = logicalType.getPrecision();
    Preconditions.checkArgument(
        precision > 0 && precision <= 76, "Precision must be in range of 1 to 76");
    Preconditions.checkArgument(scale >= 0 && scale <= 76, "Scale must be in range of 0 to 76.");
    Preconditions.checkArgument(
        scale <= precision,
        "Invalid decimal scale: %s (greater than precision: %s)",
        scale,
        precision);

    if (schema.getType() == Schema.Type.FIXED) {
      if (schema.getFixedSize() <= 16) {
        return new ArrowType.Decimal(precision, scale, 128);
      } else {
        return new ArrowType.Decimal(precision, scale, 256);
      }
    } else {
      if (precision <= 38) {
        return new ArrowType.Decimal(precision, scale, 128);
      } else {
        return new ArrowType.Decimal(precision, scale, 256);
      }
    }
  }

  private static Consumer createSkipConsumer(Schema schema) {

    SkipFunction skipFunction;
    Schema.Type type = schema.getType();

    switch (type) {
      case UNION:
        List<Consumer> unionDelegates =
            schema.getTypes().stream().map(s -> createSkipConsumer(s)).collect(Collectors.toList());
        skipFunction = decoder -> unionDelegates.get(decoder.readInt()).consume(decoder);

        break;
      case ARRAY:
        Consumer elementDelegate = createSkipConsumer(schema.getElementType());
        skipFunction =
            decoder -> {
              for (long i = decoder.skipArray(); i != 0; i = decoder.skipArray()) {
                for (long j = 0; j < i; j++) {
                  elementDelegate.consume(decoder);
                }
              }
            };
        break;
      case MAP:
        Consumer valueDelegate = createSkipConsumer(schema.getValueType());
        skipFunction =
            decoder -> {
              for (long i = decoder.skipMap(); i != 0; i = decoder.skipMap()) {
                for (long j = 0; j < i; j++) {
                  decoder.skipString(); // Discard key
                  valueDelegate.consume(decoder);
                }
              }
            };
        break;
      case RECORD:
        List<Consumer> delegates =
            schema.getFields().stream()
                .map(field -> createSkipConsumer(field.schema()))
                .collect(Collectors.toList());

        skipFunction =
            decoder -> {
              for (Consumer consumer : delegates) {
                consumer.consume(decoder);
              }
            };

        break;
      case ENUM:
        skipFunction = decoder -> decoder.readEnum();
        break;
      case STRING:
        skipFunction = decoder -> decoder.skipString();
        break;
      case FIXED:
        skipFunction = decoder -> decoder.skipFixed(schema.getFixedSize());
        break;
      case INT:
        skipFunction = decoder -> decoder.readInt();
        break;
      case BOOLEAN:
        skipFunction = decoder -> decoder.skipFixed(1);
        break;
      case LONG:
        skipFunction = decoder -> decoder.readLong();
        break;
      case FLOAT:
        skipFunction = decoder -> decoder.readFloat();
        break;
      case DOUBLE:
        skipFunction = decoder -> decoder.readDouble();
        break;
      case BYTES:
        skipFunction = decoder -> decoder.skipBytes();
        break;
      case NULL:
        skipFunction = decoder -> {};
        break;
      default:
        // no-op, shouldn't get here
        throw new UnsupportedOperationException("Invalid avro type: " + type.getName());
    }

    return new SkipConsumer(skipFunction);
  }

  static org.apache.arrow.vector.types.pojo.Schema createArrowSchema(
      Schema schema, AvroToArrowConfig config) {

    // Create an Arrow schema matching the structure of vectors built by createCompositeConsumer()

    Set<String> skipFieldNames = config.getSkipFieldNames();
    List<Field> arrowFields = new ArrayList<>(schema.getFields().size());

    Schema.Type type = schema.getType();
    if (type == Schema.Type.RECORD) {
      for (Schema.Field field : schema.getFields()) {
        if (!skipFieldNames.contains(field.name())) {
          Field arrowField = avroSchemaToField(field.schema(), field.name(), config);
          arrowFields.add(arrowField);
        }
      }
    } else {
      Field arrowField = avroSchemaToField(schema, schema.getName(), config);
      arrowFields.add(arrowField);
    }

    return new org.apache.arrow.vector.types.pojo.Schema(arrowFields);
  }

  static CompositeAvroConsumer createCompositeConsumer(Schema schema, AvroToArrowConfig config) {

    List<Consumer> consumers = new ArrayList<>();
    final Set<String> skipFieldNames = config.getSkipFieldNames();

    Schema.Type type = schema.getType();
    if (type == Schema.Type.RECORD) {
      for (Schema.Field field : schema.getFields()) {
        if (skipFieldNames.contains(field.name())) {
          consumers.add(createSkipConsumer(field.schema()));
        } else {
          Consumer consumer = createConsumer(field.schema(), field.name(), config);
          consumers.add(consumer);
        }
      }
    } else {
      Consumer consumer = createConsumer(schema, "", config);
      consumers.add(consumer);
    }

    return new CompositeAvroConsumer(consumers);
  }

  private static FieldVector createVector(
      FieldVector consumerVector, FieldType fieldType, String name, BufferAllocator allocator) {
    return consumerVector != null
        ? consumerVector
        : fieldType.createNewSingleVector(name, allocator, null);
  }

  private static String getDefaultFieldName(ArrowType type) {
    Types.MinorType minorType = Types.getMinorTypeForArrowType(type);
    return minorType.name().toLowerCase(Locale.ROOT);
  }

  private static Field avroSchemaToField(Schema schema, String name, AvroToArrowConfig config) {
    return avroSchemaToField(schema, name, false, config, null);
  }

  private static Field avroSchemaToField(
      Schema schema, String name, AvroToArrowConfig config, Map<String, String> externalProps) {
    return avroSchemaToField(schema, name, false, config, externalProps);
  }

  private static Field avroSchemaToField(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      Map<String, String> externalProps) {

    final Schema.Type type = schema.getType();
    final LogicalType logicalType = schema.getLogicalType();
    final List<Field> children = new ArrayList<>();
    final FieldType fieldType;

    switch (type) {
      case UNION:
        boolean nullableUnion =
            schema.getTypes().stream().anyMatch(t -> t.getType() == Schema.Type.NULL);
        if (nullableUnion && schema.getTypes().size() == 2 && !config.isLegacyMode()) {
          // For a simple nullable (null | type), interpret the union as a single nullable field.
          // Not available in legacy mode, which uses the literal interpretation instead
          Schema childSchema =
              schema.getTypes().get(0).getType() == Schema.Type.NULL
                  ? schema.getTypes().get(1)
                  : schema.getTypes().get(0);
          return avroSchemaToField(childSchema, name, true, config, externalProps);
        } else {
          // Literal interpretation of a union, which may or may not include a null element.
          for (int i = 0; i < schema.getTypes().size(); i++) {
            Schema childSchema = schema.getTypes().get(i);
            // Union child vector should use default name
            children.add(avroSchemaToField(childSchema, null, nullableUnion, config, null));
          }
          fieldType =
              createFieldType(
                  new ArrowType.Union(UnionMode.Sparse, null), schema, externalProps, config);
        }
        break;
      case ARRAY:
        Schema elementSchema = schema.getElementType();
        children.add(avroSchemaToField(elementSchema, ListVector.DATA_VECTOR_NAME, config));
        fieldType = createFieldType(nullable, new ArrowType.List(), schema, externalProps, config);
        break;
      case MAP:
        // MapVector internal struct field and key field should be non-nullable
        FieldType keyFieldType =
            new FieldType(/* nullable= */ false, new ArrowType.Utf8(), /* dictionary= */ null);
        Field keyField = new Field(MapVector.KEY_NAME, keyFieldType, /* children= */ null);
        Field valueField = avroSchemaToField(schema.getValueType(), MapVector.VALUE_NAME, config);

        FieldType structFieldType =
            new FieldType(false, new ArrowType.Struct(), /* dictionary= */ null);
        Field structField =
            new Field(
                MapVector.DATA_VECTOR_NAME, structFieldType, Arrays.asList(keyField, valueField));
        children.add(structField);
        fieldType =
            createFieldType(
                nullable,
                new ArrowType.Map(/* keysSorted= */ false),
                schema,
                externalProps,
                config);
        break;
      case RECORD:
        final Set<String> skipFieldNames = config.getSkipFieldNames();
        for (int i = 0; i < schema.getFields().size(); i++) {
          final Schema.Field field = schema.getFields().get(i);
          Schema childSchema = field.schema();
          String fullChildName = String.format("%s.%s", name, field.name());
          if (!skipFieldNames.contains(fullChildName)) {
            final Map<String, String> extProps = new HashMap<>();
            String doc = field.doc();
            Set<String> aliases = field.aliases();
            if (doc != null) {
              extProps.put("doc", doc);
            }
            if (aliases != null && (!aliases.isEmpty() || config.isLegacyMode())) {
              extProps.put("aliases", convertAliases(aliases));
            }
            children.add(avroSchemaToField(childSchema, fullChildName, config, extProps));
          }
        }
        fieldType =
            createFieldType(nullable, new ArrowType.Struct(), schema, externalProps, config);
        break;
      case ENUM:
        DictionaryProvider.MapDictionaryProvider provider = config.getProvider();
        int current = provider.getDictionaryIds().size();
        int enumCount = schema.getEnumSymbols().size();
        ArrowType.Int indexType = DictionaryEncoder.getIndexType(enumCount);

        fieldType =
            createFieldType(
                nullable,
                indexType,
                schema,
                externalProps,
                new DictionaryEncoding(current, /* ordered= */ false, /* indexType= */ indexType),
                config);
        break;

      case STRING:
        fieldType = createFieldType(nullable, new ArrowType.Utf8(), schema, externalProps, config);
        break;
      case FIXED:
        final ArrowType fixedArrowType;
        if (logicalType instanceof LogicalTypes.Decimal) {
          fixedArrowType = createDecimalArrowType((LogicalTypes.Decimal) logicalType, schema);
        } else {
          fixedArrowType = new ArrowType.FixedSizeBinary(schema.getFixedSize());
        }
        fieldType = createFieldType(nullable, fixedArrowType, schema, externalProps, config);
        break;
      case INT:
        final ArrowType intArrowType;
        if (logicalType instanceof LogicalTypes.Date) {
          intArrowType = new ArrowType.Date(DateUnit.DAY);
        } else if (logicalType instanceof LogicalTypes.TimeMillis) {
          intArrowType = new ArrowType.Time(TimeUnit.MILLISECOND, 32);
        } else {
          intArrowType = new ArrowType.Int(32, /* isSigned= */ true);
        }
        fieldType = createFieldType(nullable, intArrowType, schema, externalProps, config);
        break;
      case BOOLEAN:
        fieldType = createFieldType(nullable, new ArrowType.Bool(), schema, externalProps, config);
        break;
      case LONG:
        final ArrowType longArrowType;
        if (logicalType instanceof LogicalTypes.TimeMicros) {
          longArrowType = new ArrowType.Time(TimeUnit.MICROSECOND, 64);
        } else if (logicalType instanceof LogicalTypes.TimestampMillis) {
          // In legacy mode the timestamp-xxx types are treated as local
          String tz = config.isLegacyMode() ? null : "UTC";
          longArrowType = new ArrowType.Timestamp(TimeUnit.MILLISECOND, tz);
        } else if (logicalType instanceof LogicalTypes.TimestampMicros) {
          String tz = config.isLegacyMode() ? null : "UTC";
          longArrowType = new ArrowType.Timestamp(TimeUnit.MICROSECOND, tz);
        } else if (logicalType instanceof LogicalTypes.TimestampNanos) {
          String tz = config.isLegacyMode() ? null : "UTC";
          longArrowType = new ArrowType.Timestamp(TimeUnit.NANOSECOND, tz);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampMillis
            && !config.isLegacyMode()) {
          // In legacy mode the local-timestamp-xxx types are not recognized (result is just type =
          // long)
          longArrowType = new ArrowType.Timestamp(TimeUnit.MILLISECOND, null);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampMicros
            && !config.isLegacyMode()) {
          longArrowType = new ArrowType.Timestamp(TimeUnit.MICROSECOND, null);
        } else if (logicalType instanceof LogicalTypes.LocalTimestampNanos
            && !config.isLegacyMode()) {
          longArrowType = new ArrowType.Timestamp(TimeUnit.NANOSECOND, null);
        } else {
          longArrowType = new ArrowType.Int(64, /* isSigned= */ true);
        }
        fieldType = createFieldType(nullable, longArrowType, schema, externalProps, config);
        break;
      case FLOAT:
        fieldType =
            createFieldType(
                nullable, new ArrowType.FloatingPoint(SINGLE), schema, externalProps, config);
        break;
      case DOUBLE:
        fieldType =
            createFieldType(
                nullable, new ArrowType.FloatingPoint(DOUBLE), schema, externalProps, config);
        break;
      case BYTES:
        final ArrowType bytesArrowType;
        if (logicalType instanceof LogicalTypes.Decimal) {
          bytesArrowType = createDecimalArrowType((LogicalTypes.Decimal) logicalType, schema);
        } else {
          bytesArrowType = new ArrowType.Binary();
        }
        fieldType = createFieldType(nullable, bytesArrowType, schema, externalProps, config);
        break;
      case NULL:
        fieldType = createFieldType(ArrowType.Null.INSTANCE, schema, externalProps, config);
        break;
      default:
        // no-op, shouldn't get here
        throw new UnsupportedOperationException();
    }

    if (name == null) {
      name = getDefaultFieldName(fieldType.getType());
    }
    if (name.contains(".") && !config.isLegacyMode()) {
      // Do not include namespace as part of the field name
      name = name.substring(name.lastIndexOf(".") + 1);
    }
    return new Field(name, fieldType, children.size() == 0 ? null : children);
  }

  private static Consumer createArrayConsumer(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      FieldVector consumerVector) {

    ListVector listVector;
    if (consumerVector == null) {
      final Field field =
          avroSchemaToField(schema, name, nullable, config, /* externalProps= */ null);
      listVector = (ListVector) field.createVector(config.getAllocator());
    } else {
      listVector = (ListVector) consumerVector;
    }

    FieldVector dataVector = listVector.getDataVector();

    // create delegate
    Schema childSchema = schema.getElementType();
    Consumer delegate = createConsumer(childSchema, childSchema.getName(), config, dataVector);

    return new AvroArraysConsumer(listVector, delegate);
  }

  private static Consumer createStructConsumer(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      FieldVector consumerVector) {

    final Set<String> skipFieldNames = config.getSkipFieldNames();

    StructVector structVector;
    if (consumerVector == null) {
      final Field field =
          avroSchemaToField(schema, name, nullable, config, createExternalProps(schema, config));
      structVector = (StructVector) field.createVector(config.getAllocator());
    } else {
      structVector = (StructVector) consumerVector;
    }

    Consumer[] delegates = new Consumer[schema.getFields().size()];
    int vectorIndex = 0;
    for (int i = 0; i < schema.getFields().size(); i++) {
      Schema.Field childField = schema.getFields().get(i);
      Consumer delegate;
      // use full name to distinguish fields have same names between parent and child fields.
      final String fullChildName = String.format("%s.%s", name, childField.name());
      if (skipFieldNames.contains(fullChildName)) {
        delegate = createSkipConsumer(childField.schema());
      } else {
        delegate =
            createConsumer(
                childField.schema(),
                fullChildName,
                config,
                structVector.getChildrenFromFields().get(vectorIndex++));
      }

      delegates[i] = delegate;
    }

    return new AvroStructConsumer(structVector, delegates);
  }

  private static Consumer createEnumConsumer(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      FieldVector consumerVector) {

    BaseIntVector indexVector;
    if (consumerVector == null) {
      final Field field =
          avroSchemaToField(schema, name, nullable, config, createExternalProps(schema, config));
      indexVector = (BaseIntVector) field.createVector(config.getAllocator());
    } else {
      indexVector = (BaseIntVector) consumerVector;
    }

    final int valueCount = schema.getEnumSymbols().size();
    VarCharVector dictVector = new VarCharVector(name, config.getAllocator());
    dictVector.allocateNewSafe();
    dictVector.setValueCount(valueCount);
    for (int i = 0; i < valueCount; i++) {
      dictVector.set(i, schema.getEnumSymbols().get(i).getBytes(StandardCharsets.UTF_8));
    }
    Dictionary dictionary = new Dictionary(dictVector, indexVector.getField().getDictionary());
    config.getProvider().put(dictionary);

    return new AvroEnumConsumer(indexVector);
  }

  private static Consumer createMapConsumer(
      Schema schema,
      String name,
      boolean nullable,
      AvroToArrowConfig config,
      FieldVector consumerVector) {

    MapVector mapVector;
    if (consumerVector == null) {
      final Field field =
          avroSchemaToField(schema, name, nullable, config, /* externalProps= */ null);
      mapVector = (MapVector) field.createVector(config.getAllocator());
    } else {
      mapVector = (MapVector) consumerVector;
    }

    // create delegate struct consumer
    StructVector structVector = (StructVector) mapVector.getDataVector();

    // keys in avro map are always assumed to be strings.
    Consumer keyConsumer =
        new AvroStringConsumer((VarCharVector) structVector.getChildrenFromFields().get(0));
    Consumer valueConsumer =
        createConsumer(
            schema.getValueType(),
            schema.getValueType().getName(),
            config,
            structVector.getChildrenFromFields().get(1));

    AvroStructConsumer internalConsumer =
        new AvroStructConsumer(structVector, new Consumer[] {keyConsumer, valueConsumer});

    return new AvroMapConsumer(mapVector, internalConsumer);
  }

  private static Consumer createUnionConsumer(
      Schema schema,
      String name,
      boolean nullableUnion,
      AvroToArrowConfig config,
      FieldVector consumerVector) {
    final int size = schema.getTypes().size();

    UnionVector unionVector;
    if (consumerVector == null) {
      final Field field = avroSchemaToField(schema, name, config);
      unionVector = (UnionVector) field.createVector(config.getAllocator());
    } else {
      unionVector = (UnionVector) consumerVector;
    }

    List<FieldVector> childVectors = unionVector.getChildrenFromFields();

    Consumer[] delegates = new Consumer[size];
    Types.MinorType[] types = new Types.MinorType[size];

    for (int i = 0; i < size; i++) {
      FieldVector child = childVectors.get(i);
      Schema subSchema = schema.getTypes().get(i);
      Consumer delegate =
          createConsumer(subSchema, subSchema.getName(), nullableUnion, config, child);
      delegates[i] = delegate;
      types[i] = child.getMinorType();
    }
    return new AvroUnionsConsumer(unionVector, delegates, types);
  }

  /**
   * Read data from {@link Decoder} and generate a {@link VectorSchemaRoot}.
   *
   * @param schema avro schema
   * @param decoder avro decoder to read data from
   */
  static VectorSchemaRoot avroToArrowVectors(
      Schema schema, Decoder decoder, AvroToArrowConfig config) throws IOException {

    List<FieldVector> vectors = new ArrayList<>();
    List<Consumer> consumers = new ArrayList<>();
    final Set<String> skipFieldNames = config.getSkipFieldNames();

    Schema.Type type = schema.getType();
    if (type == Schema.Type.RECORD) {
      for (Schema.Field field : schema.getFields()) {
        if (skipFieldNames.contains(field.name())) {
          consumers.add(createSkipConsumer(field.schema()));
        } else {
          Consumer consumer = createConsumer(field.schema(), field.name(), config);
          consumers.add(consumer);
          vectors.add(consumer.getVector());
        }
      }
    } else {
      Consumer consumer = createConsumer(schema, "", config);
      consumers.add(consumer);
      vectors.add(consumer.getVector());
    }

    long validConsumerCount = consumers.stream().filter(c -> !c.skippable()).count();
    Preconditions.checkArgument(
        vectors.size() == validConsumerCount, "vectors size not equals consumers size.");

    List<Field> fields = vectors.stream().map(t -> t.getField()).collect(Collectors.toList());

    VectorSchemaRoot root = new VectorSchemaRoot(fields, vectors, 0);

    CompositeAvroConsumer compositeConsumer = new CompositeAvroConsumer(consumers);

    int valueCount = 0;
    try {
      while (true) {
        ValueVectorUtility.ensureCapacity(root, valueCount + 1);
        compositeConsumer.consume(decoder);
        valueCount++;
      }
    } catch (EOFException eof) {
      // reach the end of encoder stream.
      root.setRowCount(valueCount);
    } catch (Exception e) {
      compositeConsumer.close();
      throw new UnsupportedOperationException("Error occurs while consume process.", e);
    }

    return root;
  }

  // Do not include props that are part of the Avro format itself as field metadata
  // These are already represented in the field / type structure and are not custom attributes
  private static final List<String> AVRO_FORMAT_METADATA =
      Arrays.asList("logicalType", "precision", "scale");

  private static Map<String, String> getMetaData(Schema schema, AvroToArrowConfig config) {
    Map<String, String> metadata = new HashMap<>();
    for (Map.Entry<String, Object> prop : schema.getObjectProps().entrySet()) {
      if (!AVRO_FORMAT_METADATA.contains(prop.getKey()) || config.isLegacyMode()) {
        metadata.put(prop.getKey(), prop.getValue().toString());
      }
    }
    return metadata;
  }

  private static Map<String, String> getMetaData(
      Schema schema, Map<String, String> externalProps, AvroToArrowConfig config) {
    Map<String, String> metadata = getMetaData(schema, config);
    if (externalProps != null) {
      metadata.putAll(externalProps);
    }
    return metadata;
  }

  /** Parse avro attributes and convert them to metadata. */
  private static Map<String, String> createExternalProps(Schema schema, AvroToArrowConfig config) {
    final Map<String, String> extProps = new HashMap<>();
    String doc = schema.getDoc();
    Set<String> aliases = schema.getAliases();
    if (doc != null) {
      extProps.put("doc", doc);
    }
    if (aliases != null && (!aliases.isEmpty() || config.isLegacyMode())) {
      extProps.put("aliases", convertAliases(aliases));
    }
    return extProps;
  }

  private static FieldType createFieldType(
      ArrowType arrowType,
      Schema schema,
      Map<String, String> externalProps,
      AvroToArrowConfig config) {
    return createFieldType(arrowType, schema, externalProps, /* dictionary= */ null, config);
  }

  private static FieldType createFieldType(
      boolean nullable,
      ArrowType arrowType,
      Schema schema,
      Map<String, String> externalProps,
      AvroToArrowConfig config) {
    return createFieldType(
        nullable, arrowType, schema, externalProps, /* dictionary= */ null, config);
  }

  private static FieldType createFieldType(
      ArrowType arrowType,
      Schema schema,
      Map<String, String> externalProps,
      DictionaryEncoding dictionary,
      AvroToArrowConfig config) {

    return createFieldType(
        /* nullable= */ false, arrowType, schema, externalProps, dictionary, config);
  }

  private static FieldType createFieldType(
      boolean nullable,
      ArrowType arrowType,
      Schema schema,
      Map<String, String> externalProps,
      DictionaryEncoding dictionary,
      AvroToArrowConfig config) {

    return new FieldType(
        nullable, arrowType, dictionary, getMetaData(schema, externalProps, config));
  }

  private static String convertAliases(Set<String> aliases) {
    JsonStringArrayList jsonList = new JsonStringArrayList();
    aliases.stream().forEach(a -> jsonList.add(a));
    return jsonList.toString();
  }
}
