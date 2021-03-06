package io.eels.component.avro

import com.sksamuel.exts.Logging
import io.eels.schema._
import org.apache.avro.LogicalTypes._
import org.apache.avro.{LogicalTypes, Schema, SchemaBuilder}
import org.codehaus.jackson.node.NullNode

import scala.collection.JavaConverters._

object AvroSchemaFns extends Logging {

  def toAvroSchema(structType: StructType,
                   caseSensitive: Boolean = true,
                   name: String = "row",
                   namespace: String = "namespace"): org.apache.avro.Schema = {

    def toAvroSchema(dataType: DataType): org.apache.avro.Schema = dataType match {
      case ArrayType(elementType) => SchemaBuilder.array().items(toAvroSchema(elementType))
      case BigIntType => SchemaBuilder.builder().longType()
      case BinaryType => Schema.create(Schema.Type.BYTES)
      case BooleanType => SchemaBuilder.builder().booleanType()
      case CharType(size) => Schema.createFixed("char", null, "", size)
      case DateType =>
        val schema = Schema.create(Schema.Type.INT)
        LogicalTypes.date().addToSchema(schema)
        schema
      case DecimalType(precision, scale) =>
        val schema = Schema.create(Schema.Type.BYTES)
        LogicalTypes.decimal(precision.value, scale.value).addToSchema(schema)
        schema
      case DoubleType => SchemaBuilder.builder().doubleType()
      case EnumType(enumName, values) => SchemaBuilder.enumeration(enumName).symbols(values: _*)
      case FloatType => SchemaBuilder.builder().floatType()
      case i: IntType => SchemaBuilder.builder().intType()
      case l: LongType => SchemaBuilder.builder().longType()
      case s: ShortType => SchemaBuilder.builder().intType()
      case MapType(StringType, valueType) => SchemaBuilder.map().values(toAvroSchema(valueType))
      case MapType(other, valueType) => sys.error("Avro only supports maps with String keys")
      case StringType => SchemaBuilder.builder().stringType()
      case StructType(structFields) =>
        val record = SchemaBuilder.record("record").fields()
        structFields.map { field =>
          val avro = toAvroSchema(field.dataType)
          record.name(field.name).`type`(avro).noDefault()
        }
        record.endRecord()
      case TimeMillisType =>
        val schema = Schema.create(Schema.Type.INT)
        LogicalTypes.timeMillis().addToSchema(schema)
        schema
      case TimeMicrosType =>
        val schema = Schema.create(Schema.Type.LONG)
        LogicalTypes.timeMicros().addToSchema(schema)
        schema
      case TimestampMillisType =>
        val schema = Schema.create(Schema.Type.LONG)
        LogicalTypes.timestampMillis().addToSchema(schema)
        schema
      case TimestampMicrosType =>
        val schema = Schema.create(Schema.Type.LONG)
        LogicalTypes.timestampMicros().addToSchema(schema)
        schema
      case VarcharType(_) => SchemaBuilder.builder().stringType()
    }

    def toAvroField(field: Field, caseSensitive: Boolean = true): org.apache.avro.Schema.Field = {

      val avroSchema = toAvroSchema(field.dataType)
      val fieldName = if (caseSensitive) field.name else field.name.toLowerCase()

      if (field.nullable) {
        val union = SchemaBuilder.unionOf().nullType().and().`type`(avroSchema).endUnion()
        new org.apache.avro.Schema.Field(fieldName, union, null, NullNode.getInstance())
      } else {
        new org.apache.avro.Schema.Field(fieldName, avroSchema, null: String, null: Object)
      }
    }

    val fields = structType.fields.map { field => toAvroField(field, caseSensitive) }
    val schema = org.apache.avro.Schema.createRecord(name, null, namespace, false)
    schema.setFields(fields.asJava)
    schema
  }

  def fromAvroSchema(schema: Schema, forceNullables: Boolean = false): StructType = {
    require(schema.getType == Schema.Type.RECORD)

    def fromAvroField(field: org.apache.avro.Schema.Field): Field = {
      val nullable = forceNullables ||
        field.schema.getType == Schema.Type.UNION &&
          field.schema.getTypes.asScala.map(_.getType).contains(Schema.Type.NULL)
      Field(field.name, toDataType(field.schema), nullable)
    }

    def toDataType(schema: Schema): DataType = schema.getType match {
      case org.apache.avro.Schema.Type.ARRAY => ArrayType.cached(toDataType(schema.getElementType))
      case org.apache.avro.Schema.Type.BOOLEAN => BooleanType
      case org.apache.avro.Schema.Type.BYTES => schema.getLogicalType match {
        case decimal: Decimal => DecimalType(Precision(decimal.getPrecision), Scale(decimal.getScale))
        case _ => BinaryType
      }
      case org.apache.avro.Schema.Type.DOUBLE => DoubleType
      case org.apache.avro.Schema.Type.ENUM => EnumType(schema.getName, schema.getEnumSymbols.asScala)
      case org.apache.avro.Schema.Type.FIXED => CharType(schema.getFixedSize)
      case org.apache.avro.Schema.Type.FLOAT => FloatType
      case org.apache.avro.Schema.Type.INT =>
        schema.getLogicalType match {
          case _: Date => DateType
          case _: TimeMillis => TimeMillisType
          case _ => IntType.Signed
        }
      case org.apache.avro.Schema.Type.LONG => schema.getLogicalType match {
        case _: TimeMicros => TimeMicrosType
        case _: TimestampMillis => TimestampMillisType
        case _: TimestampMicros => TimestampMicrosType
        case _ => LongType.Signed
      }
      case org.apache.avro.Schema.Type.MAP => MapType(StringType, toDataType(schema.getValueType))
      case org.apache.avro.Schema.Type.RECORD => StructType(schema.getFields.asScala.map(fromAvroField).toList)
      case org.apache.avro.Schema.Type.STRING => StringType
      case org.apache.avro.Schema.Type.UNION =>
        toDataType(schema.getTypes.asScala.filterNot(_.getType == Schema.Type.NULL).head)
    }

    toDataType(schema).asInstanceOf[StructType]
  }
}