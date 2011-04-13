package com.twitter.scrooge

import com.twitter.scrooge.AST._
import org.apache.thrift.protocol.TProtocol

trait ThriftStruct {
  def write(oprot: TProtocol)
}

object ScalaGenerator {

  val header =
"""/**
 * Autogenerated by Scrooge
 * Edit this shit, I dare you.
 */

package {{scalaNamespace}}

"""

  val enumTemplateText =
header + """import org.apache.thrift.TEnum

object {{name}} {
{{values.map { v => "case object " + v.name + " extends " + name + "(" + v.value + ")"}.indent}}

  def apply(value: Int): Option[{{name}}] = {
    value match {
{{values.map { v => "case " + v.value + " => Some(" + v.name + ")"}.indent(3)}}
      case _ => None
    }
  }
}

abstract class {{name}}(val value: Int) {
  def toThrift = new TEnum {
    override def getValue = {
      value
    }
  }
}"""

  val constsTemplateText =
header + """object Constants {
{{constList.map { e => constTemplate(e, scope) }.indent}}
}
"""

  val constTemplateText = "val {{name}}: {{scalaType(`type`)}} = {{constantTemplate(`type`, value)}}"

  val basicReadFieldTemplateText =
"""case {{id.toString}} => { /* {{name}} */
  field.`type` match {
    case TType.{{constType(`type`)}} => {{name}} = iprot.{{protocolReadMethod(`type`)}}
    case _ => TProtocolUtil.skip(iprot, field.`type`)
  }
}"""

  val basicWriteFieldTemplateText =
"""oprot.writeFieldBegin({{writeFieldConst(name)}})
oprot.{{protocolWriteMethod(`type`)}}({{name}})
oprot.writeFieldEnd()"""

  val stringWriteFieldTemplateText =
"""if ({{name}} != null) {
  oprot.writeFieldBegin({{writeFieldConst(name)}})
  oprot.writeString({{name}})
  oprot.writeFieldEnd()
}"""

  val structTemplateText =
header + """import org.apache.thrift.protocol._
import com.twitter.scrooge.ThriftStruct

object {{name}} {
  object decoder extends (TProtocol => ThriftStruct) {
    override def apply(iprot: TProtocol) = {
      var field: TField = null
{{fields.map { f => "var " + f.name + defaultValueTemplate(f) }.indent(3)}}
      var done = false
      while (!done) {
        field = iprot.readFieldBegin
        if (field.`type` == TType.STOP) {
          done = true
        } else {
          field.id match {
{{fields.map { f => structReadFieldTemplate(f)(f, scope) }.indent(6) }}
            case _ => TProtocolUtil.skip(iprot, field.`type`)
          }
          iprot.readFieldEnd()
        }
      }
      iprot.readStructEnd()
      new {{name}}({{fields.map { f => f.name }.mkString(", ")}})
    }
  }
}

case class {{name}}({{fields.map { f => f.name + ": " + scalaType(f.`type`) }.mkString(", ")}}) extends ThriftStruct {
  private val STRUCT_DESC = new TStruct("{{name}}")
{{fields.map { f => "private val " + writeFieldConst(f.name) + " = new TField(\"" + f.name + "\", TType." + constType(f.`type`) + ", " + f.id.toString + ")"}.indent}}

  override def write(oprot: TProtocol) {
    validate()

    oprot.writeStructBegin(STRUCT_DESC)
{{fields.map { f => structWriteFieldTemplate(f)(f, scope) }.indent(2) }}
    oprot.writeFieldStop()
    oprot.writeStructEnd()
  }

  def validate() = true //TODO: Implement this
}"""

  val serviceTemplateText =
"""// autogenerated by scrooge.

package {{scalaNamespace}}

import java.nio.ByteBuffer
import scala.collection.{Map, Set}
import com.twitter.conversions.time._
import com.twitter.finagle.builder._
import com.twitter.finagle.stats._
import com.twitter.finagle.thrift._
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.Service
import org.apache.thrift.protocol._

/*
import java.net.InetSocketAddress
import java.util.{List => JList, Map => JMap, Set => JSet}
import scala.collection.mutable

import scala.collection.JavaConversions._
import com.twitter.util._
*/

trait {{service.name}} {{service.parent.map { "extends " + _ + " " }.getOrElse("")}}{
  implicit def voidUnit(f: Future[_]): Future[java.lang.Void] = f.map(x=>null)

{{service.functions.map { f => "  " + serviceFunctionTemplate(f, scope) }.mkString("\n")}}

  def toThrift = new {{service.name}}.ThriftAdapter(this)
}

object {{service.name}} {
  trait Server extends Service with {{service.name}} {
    val log = Logger.get(getClass)

    def thriftCodec = ThriftServerFramedCodec()
    val thriftProtocolFactory = new TBinaryProtocol.Factory()
    val thriftPort: Int
    val serverName: String

    var server: Server = null

    def start = {
      val thriftImpl = new {{javaNamespace}}.{{service.name}}.Service(toThrift, thriftProtocolFactory)
      val serverAddr = new InetSocketAddress(thriftPort)
      server = ServerBuilder().codec(thriftCodec).name(serverName).reportTo(new OstrichStatsReceiver).bindTo(serverAddr).build(thriftImpl)
    }

    def shutdown = synchronized {
      if (server != null) {
        server.close(0.seconds)
      }
    }
  }

  class ThriftAdapter(val self: {{service.name}}) extends {{javaNamespace}}.{{service.name}}.ServiceIface {
    val log = Logger.get(getClass)

{{service.functions.map { f => "    " + serviceFunctionAdapterTemplate(f, scope) }.mkString("\n")}}
  }
}


     class <%=obj%>ThriftAdapter(val <%=obj.to_s.camelize%>: <%=obj%>) extends <%=tnamespace%>.<%=obj%>.ServiceIface {

        <% for m in methods do %>
          def <%=m.name.downcase%>(<%=m.args.map{|f| f[:name].camelize + ": " + type_of(f, true)}.join(", ") %>) = <%="try" if $exception %> {
            <%=obj.to_s.camelize%>.<%=m.name.camelize%>(<%=m.args.map{|f| wrapper(f) }.join(", ")%>)
            <% if m.retval %>
              .map { retval =>
                <% unwrap(m.retval) do %>retval<%end%>
              }
            <% end %>
          <% if $exception %>
            } catch {
              case t: org.apache.thrift.TBase[_,_] => throw(t)
              case t: Throwable => {
                log.error(t, "Uncaught error: %s", t)

                throw new <%=tnamespace%>.<%=last $exception%>(t.getMessage)
              }
          <% end %>
          }
        <% end %>
      }

      class <%=obj%>ClientAdapter(val <%=obj.to_s.camelize%>: <%=tnamespace%>.<%=obj%>.ServiceIface) extends <%=obj%> {
        val log = Logger.get(getClass)

        <% for m in methods do %>
          def <%=m.name.camelize%>(<%=m.args.map{|f| f[:name].camelize + ": " + type_of(f)}.join(", ") %>) = {
            <%=obj.to_s.camelize%>.<%=m.name.downcase%>(<%=m.args.map{|f| unwrap(f, f[:name].camelize) }.join(", ")%>)
            <% if m.retval %>
              .map { retval =>
                <%=wrapper(m.retval, "retval") %>
              }
            <% end %>
          }
        <% end %>
      }

"""

  val serviceFunctionTemplateText =
    """def {{name}}({{ args.map { f => serviceArgTemplate(f, scope) }.mkString(", ") }}): Future[{{scalaType(`type`)}}]"""

  val serviceArgTemplateText =
    """{{name}}: {{if (optional) "Option[" else ""}}{{scalaType(`type`)}}{{if (optional) "]" else ""}}"""

  val serviceFunctionAdapterTemplateText =
"""
def {{name}}({{ args.map { f => f.name + ": " + javaType(f.`type`) }.mkString(", ") }}) = {{if (throws.size > 0) "try " else ""}}{
  self.{{name}}({{args.map { f => javaize(f.name, f.`type`) }.mkString(", ")}}).map { rv => {{scalaize("rv", `type`)}} }
}

"""

  case class ScalaService(scalaNamespace: String, javaNamespace: String, service: Service)
  case class ConstList(constList: Array[Const])
}

// maybe should eventually go elsewhere.
class ScalaGenerator {
  import ScalaGenerator._

  var scalaNamespace: String = null
  var javaNamespace: String = null

  val serviceTemplate = Template[ScalaService](serviceTemplateText)
  val serviceFunctionTemplate = Template[Function](serviceFunctionTemplateText)
  val serviceArgTemplate = Template[Field](serviceArgTemplateText)
  val serviceFunctionAdapterTemplate = Template[Function](serviceFunctionAdapterTemplateText)
  val enumTemplate = Template[Enum](enumTemplateText)
  val constsTemplate = Template[ConstList](constsTemplateText)
  val constTemplate = Template[Const](constTemplateText)
  val structTemplate = Template[Struct](structTemplateText)

  // Constants
  val stringTemplate = Template[StringConstant](""""{{value}}"""")
  val doubleTemplate = Template[DoubleConstant]("{{value.toString}}")
  val intTemplate = Template[IntConstant]("{{value.toString}}")
  val listTemplate = Template[ListConstant](
    """List({{elems.map { e => constantTemplate(null, e) }.mkString(", ")}})"""
  )
  val mapTemplate =  Template[MapConstant](
    """Map({{elems.asInstanceOf[Map[com.twitter.scrooge.AST.Constant, com.twitter.scrooge.AST.Constant]].map { case (x, y) => constantTemplate(null, x) + " -> " + constantTemplate(null, y) }.mkString(",\n")}})"""
  )

  def constantTemplate(`type`: FieldType, constant: Constant): String = {
    constant match {
      case c @ StringConstant(_) =>
        stringTemplate(c, this)
      case c @ DoubleConstant(_) =>
        doubleTemplate(c, this)
      case c @ IntConstant(_) =>
        intTemplate(c, this)
      case c @ ListConstant(_) =>
        listTemplate(c, this)
      case c @ MapConstant(_) =>
        mapTemplate(c, this)
      case c @ Identifier(name) =>
        `type`.asInstanceOf[ReferenceType].name + "." + name
    }
  }

  def writeFieldConst(name: String) = name.toUpperCase + "_FIELD_DESC"

  def structWriteFieldTemplate(field: Field) = {
    field.`type` match {
      case TString => Template[Field](stringWriteFieldTemplateText)
      case _ => Template[Field](basicWriteFieldTemplateText)
    }
  }
  def structReadFieldTemplate(field: Field) = {
    field.`type` match {
      case _ => Template[Field](basicReadFieldTemplateText)
    }
  }

  def defaultValueTemplate(field: Field) = {
    field.`type` match {
      case c @ TI16 => {
        ": " + scalaType(c) + " = 0"
      }
      case c @ TI32 => {
        ": " + scalaType(c) + " = 0"
      }
      case c @ TI64 => {
        ": " + scalaType(c) + " = 0"
      }
      case c @ _ => {
        ": " + scalaType(c) + " = null"
      }
    }
  }

  def constType(t: FunctionType): String = {
    t match {
      case TBool => "BOOL"
      case TByte => "BYTE"
      case TI16 => "I16"
      case TI32 => "I32"
      case TI64 => "I64"
      case TDouble => "DOUBLE"
      case TString => "STRING"
      case TBinary => "STRING" // IDK why, but Binary fields are marked as String
    }
  }

  def protocolReadMethod(t: FunctionType): String = {
    t match {
      case TBool => "readBool"
      case TByte => "readByte"
      case TI16 => "readI16"
      case TI32 => "readI32"
      case TI64 => "readI64"
      case TDouble => "readDouble"
      case TString => "readString"
      case TBinary => "readBinary"
    }
  }

  def protocolWriteMethod(t: FunctionType): String = {
    t match {
      case TBool => "writeBool"
      case TByte => "writeByte"
      case TI16 => "writeI16"
      case TI32 => "writeI32"
      case TI64 => "writeI64"
      case TDouble => "writeDouble"
      case TString => "writeString"
      case TBinary => "writeBinary"
    }
  }

  def scalaType(t: FunctionType): String = {
    t match {
      case Void => "Void"
      case TBool => "Boolean"
      case TByte => "Byte"
      case TI16 => "Short"
      case TI32 => "Int"
      case TI64 => "Long"
      case TDouble => "Double"
      case TString => "String"
      case TBinary => "ByteBuffer"
      case ReferenceType(x) => x
      case MapType(k, v, _) => "Map[" + scalaType(k) + ", " + scalaType(v) + "]"
      case SetType(x, _) => "Set[" + scalaType(x) + "]"
      case ListType(x, _) => "Seq[" + scalaType(x) + "]"
    }
  }

  def javaType(t: FunctionType): String = {
    t match {
      case Void => "Void"
      case TBool => "java.lang.Boolean"
      case TByte => "java.lang.Byte"
      case TI16 => "java.lang.Short"
      case TI32 => "java.lang.Integer"
      case TI64 => "java.lang.Long"
      case TDouble => "java.lang.Double"
      case TString => "String"
      case TBinary => "ByteBuffer"
      case ReferenceType(x) => x
      case MapType(k, v, _) => "java.util.Map[" + scalaType(k) + ", " + scalaType(v) + "]"
      case SetType(x, _) => "java.util.Set[" + scalaType(x) + "]"
      case ListType(x, _) => "java.util.List[" + scalaType(x) + "]"
    }
  }

  def javaize(name: String, t: FunctionType): String = {
    t match {
      case TBool => name + ".booleanValue"
      case TByte => name + ".byteValue"
      case TI16 => name + ".shortValue"
      case TI32 => name + ".intValue"
      case TI64 => name + ".longValue"
      case TDouble => name + ".doubleValue"
      case TString => name
      case TBinary => name
      case ReferenceType(x) => x + ".toThrift"
      case MapType(k, v, _) => "asScalaMap(" + name + ").view.map { case (k, v) => (" + javaize("k", k) + ", " + javaize("v", v) + ") }"
      case SetType(x, _) => "asScalaSet(" + name + ").view.map { x => " + javaize("x", x) + " }"
      case ListType(x, _) => "asScalaBuffer(" + name + ").view.map { x => " + javaize("x", x) + " }"
    }
  }

  def scalaize(name: String, t: FunctionType): String = {
    "FIXME"
  }

  implicit def string2indent(underlying: String) = new Object {
    def indent(level: Int = 1): String = underlying.split("\\n").map { ("  " * level) + _ }.mkString("\n")
    def indent: String = indent(1)
  }
  implicit def seq2indent(underlying: Seq[String]) = new Object {
    def indent(level: Int = 1): String = underlying.mkString("\n").indent(level)
    def indent: String = indent(1)
  }
  implicit def array2indent(underlying: Array[String]) = new Object {
    def indent(level: Int = 1): String = underlying.mkString("\n").indent(level)
    def indent: String = indent(1)
  }

  def apply(enum: Enum): String = enumTemplate(enum, this)
  def apply(consts: ConstList): String = constsTemplate(consts, this)
  def apply(const: Const): String = constTemplate(const, this)
  def apply(struct: Struct): String = structTemplate(struct, this)

  def apply(doc: Document): String = {
    javaNamespace = doc.headers.collect {
      case Namespace("java", x) => x
    }.headOption.getOrElse("thrift")
    scalaNamespace = doc.headers.collect {
      case Namespace("scala", x) => x
    }.headOption.getOrElse(javaNamespace)

    apply(ConstList(doc.defs.collect { case c @ Const(_, _, _) => c }))
    doc.defs.foreach {
      case service @ Service(_, _, _) =>
        val scalaService = ScalaService(scalaNamespace, javaNamespace, service)
        println(serviceTemplate(scalaService, this))
      case enum @ Enum(_, _) =>
        apply(enum)
      case Const(_, _, _) => // Already dealt with you
      case _ => // nothing for now.
    }
    ""
  }
}


/*
  public interface ServiceIface {

    public Future<String> get(String key);

    public Future<Void> put(String key, String value);

  }

  public static class ServiceToClient implements ServiceIface {
    private com.twitter.finagle.Service<ThriftClientRequest, byte[]> service;
    private TProtocolFactory protocolFactory;
    public ServiceToClient(com.twitter.finagle.Service<ThriftClientRequest, byte[]> service, TProtocolFactory protocolFa
ctory) {
      this.service = service;
      this.protocolFactory = protocolFactory;
    }

    public Future<String> get(String key) {
      try {
        // TODO: size
.....

  public static class Service extends com.twitter.finagle.Service<byte[], byte[]> {
    private final ServiceIface iface;
    private final TProtocolFactory protocolFactory;
    protected HashMap<String, Function2<TProtocol, Integer, Future<byte[]>>> functionMap = new HashMap<String, Function2<TProtocol, Integer, Future<byte[]>>>();
    public Service(final ServiceIface iface, final TProtocolFactory protocolFactory) {
      this.iface = iface;
      this.protocolFactory = protocolFactory;
      functionMap.put("get", new Function2<TProtocol, Integer, Future<byte[]>>() {
        public Future<byte[]> apply(final TProtocol iprot, final Integer seqid) {
          get_args args = new get_args();
          try {

.....
*/
