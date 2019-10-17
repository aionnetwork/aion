package org.aion.api.server.rpc3.types;

import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import static org.aion.api.server.rpc3.types.RPCTypes.*;
import java.util.regex.Pattern;
import org.aion.api.server.rpc3.RPCExceptions.ParseErrorRPCException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigInteger;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public class RPCTypesConverter{

    private static final Pattern hexPattern= Pattern.compile("^0x[0-9a-fA-F]+");
    private static final Pattern decPattern = Pattern.compile("^[0-9]+");

    public static class ObjectConverter{

        public static String decode(Object s){
            if(s==null) return null;
            return s.toString();
        }

        public static Object encode(Object obj){
            return obj;
        }
    }

    public static class StringConverter{

        public static String decode(Object s){
            if(s==null) return null;
            return s.toString();
        }

        public static String encode(String s){
            return s;
        }
    }

    public static class LongConverter{
        private static final Pattern hexPattern = Pattern.compile("^0x[0-9a-fA-F]+");
        private static final Pattern decPattern = Pattern.compile("^[0-9]+");

        public static Long decode(Object s){
            if(s==null) return null;
            if(hexPattern.matcher(s.toString()).find()){
                return Long.parseLong(s.toString().substring(2), 16);
            }
            else if(decPattern.matcher(s.toString()).find()){
                return Long.parseLong(s.toString());
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        public static Long encode(Long s){
            try{
                return s;
            }catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encodeHex(Long s){
            try{
                return "0x"+Long.toHexString(s);
            }catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

    }


    public static class IntegerConverter{
        private final static Pattern hexPattern = Pattern.compile("^0x[0-9a-fA-F]+");
        private final static Pattern decPattern = Pattern.compile("^[0-9]+");

        public static Integer decode(Object s){
            if(s==null) return null;
            if(hexPattern.matcher(s.toString()).find()){
                return Integer.parseInt(s.toString().substring(2), 16);
            }
            else if(decPattern.matcher(s.toString()).find()){
                return Integer.parseInt(s.toString());
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        public static Integer encode(Integer s){
            try{
                return s;
            }catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encodeHex(Integer s){
            try{
                return "0x"+Integer.toHexString(s);
            }catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }
    }

    public static class BigIntegerConverter{

        public static String encodeHex(BigInteger bigInteger){
            try{
                return "0x"+bigInteger.toString(16);
            } catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(BigInteger bigInteger){
            try{
                return bigInteger.toString(16);
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static BigInteger decode(Object s){
            if(s==null) return null;

            if(hexPattern.matcher(s.toString()).find()){
                return new BigInteger(s.toString().substring(2), 16);
            }
            else if(decPattern.matcher(s.toString()).find()){
                return new BigInteger(s.toString());
            }
            else{
                throw new ParseErrorRPCException();
            }
        }
    }

    public static class ByteArrayWrapperConverter{
        private static final Pattern hexPattern = Pattern.compile("^0x[0-9a-fA-F]+");

        public static ByteArrayWrapper decode(Object obj){
            if (obj == null){
                return null;
            }
            else if(obj instanceof byte[]){
                return ByteArrayWrapper.wrap(((byte[])obj));
            }
            else if (obj instanceof String){
                if (hexPattern.matcher(((String)obj)).find()){
                    return ByteArrayWrapper.wrap(ByteUtil.hexStringToBytes((String) obj));
                } else {
                    return ByteArrayWrapper.wrap(((String)obj).getBytes());
                }
            }
            else {
                    throw new ParseErrorRPCException();
            }
        }

        public static String encode(ByteArrayWrapper bytes){
            if (bytes == null) return null;
            else return "0x" + bytes.toString();
        }
    }

    public static class AionAddressConverter{
        public static AionAddress decode(Object obj){
            try{
                if (obj == null){
                    return null;
                }
                else if (obj instanceof String && hexPattern.matcher(((String)obj)).find()){
                    return new AionAddress(ByteUtil.hexStringToBytes(((String) obj)));
                }
                else if (obj instanceof byte[]){
                    return new AionAddress(((byte[])obj));
                }
                else {
                    throw new ParseErrorRPCException();
                }
            }catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(AionAddress address){
            if (address==null) return null;
            else return "0x"+address.toString();
        }
    }

    public static class RequestConverter{
        public static Request decode(Object str){
            try{
                JSONObject jsonObject = new JSONObject(((String) str).replaceAll("\"","\""));
                return new Request( IntegerConverter.decode(jsonObject.opt("id")) , StringConverter.decode(jsonObject.opt("method")) , StringConverter.decode(jsonObject.opt("params")) , VersionTypeConverter.decode(jsonObject.opt("jsonRPC")) );
            } catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode( Request obj){
            try{
                if(obj==null) return null;
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", IntegerConverter.encode(obj.id));
                jsonObject.put("method", StringConverter.encode(obj.method));
                jsonObject.put("params", StringConverter.encode(obj.params));
                jsonObject.put("jsonRPC", VersionTypeConverter.encode(obj.jsonRPC));
                return jsonObject.toString().replaceAll("\"","\"");
            }
            catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

    }

    public static class ResponseConverter{
        public static Response decode(Object str){
            try{
                JSONObject jsonObject = new JSONObject(((String) str).replaceAll("\"","\""));
                return new Response( IntegerConverter.decode(jsonObject.opt("id")) , ObjectConverter.decode(jsonObject.opt("result")) , VersionTypeConverter.decode(jsonObject.opt("jsonRPC")) );
            } catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode( Response obj){
            try{
                if(obj==null) return null;
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", IntegerConverter.encode(obj.id));
                jsonObject.put("result", ObjectConverter.encode(obj.result));
                jsonObject.put("jsonRPC", VersionTypeConverter.encode(obj.jsonRPC));
                return jsonObject.toString().replaceAll("\"","\"");
            }
            catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

    }

    public static class DataHexStringConverter{
        private static final Pattern regex = Pattern.compile("^0x([0-9a-fA-F][0-9a-fA-F])+");

        public static ByteArrayWrapper decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return ByteArrayWrapperConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(ByteArrayWrapper obj){
            if (obj != null){
                String result = ByteArrayWrapperConverter.encode(obj);
                if(checkConstraints(result))
                    return result;
                else
                    throw new ParseErrorRPCException();
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        private static boolean checkConstraints(String s){
            return regex.matcher(s).find() && s.length() >= 4 && s.length() <= 2147483647;
        }
    }

    public static class HexStringConverter{
        private static final Pattern regex = Pattern.compile("^0x[0-9a-fA-F]+");

        public static BigInteger decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return BigIntegerConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(BigInteger obj){
            if (obj != null){
                String result = BigIntegerConverter.encodeHex(obj);
                if(checkConstraints(result))
                    return result;
                else
                    throw new ParseErrorRPCException();
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        private static boolean checkConstraints(String s){
            return regex.matcher(s).find() && s.length() >= 3 && s.length() <= 2147483647;
        }
    }

    public static class LongHexStringConverter{
        private static final Pattern regex = Pattern.compile("^0x[0-9a-fA-F]+");

        public static Long decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return LongConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(Long obj){
            if (obj != null){
                String result = LongConverter.encodeHex(obj);
                if(checkConstraints(result))
                    return result;
                else
                    throw new ParseErrorRPCException();
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        private static boolean checkConstraints(String s){
            return regex.matcher(s).find() && s.length() >= 3 && s.length() <= 19;
        }
    }

    public static class IntHexStringConverter{
        private static final Pattern regex = Pattern.compile("^0x[0-9a-fA-F]+");

        public static Integer decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return IntegerConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(Integer obj){
            if (obj != null){
                String result = IntegerConverter.encodeHex(obj);
                if(checkConstraints(result))
                    return result;
                else
                    throw new ParseErrorRPCException();
            }
            else{
                throw new ParseErrorRPCException();
            }
        }

        private static boolean checkConstraints(String s){
            return regex.matcher(s).find() && s.length() >= 3 && s.length() <= 11;
        }
    }

    public static class EcRecoverParamsConverter{
        public static EcRecoverParams decode(Object object){
            String s = object.toString().replaceAll("\"","\"");
            try{
                EcRecoverParams obj;
                if(s.startsWith("[") && s.endsWith("]")){
                    JSONArray jsonArray = new JSONArray(s);
                    obj = new EcRecoverParams( DataHexStringConverter.decode(jsonArray.opt(0)), DataHexStringConverter.decode(jsonArray.opt(1)));
                }
                else if(s.startsWith("{") && s.endsWith("}")){
                    JSONObject jsonObject = new JSONObject(s);
                    obj = new EcRecoverParams( DataHexStringConverter.decode(jsonObject.opt("dataThatWasSigned")), DataHexStringConverter.decode(jsonObject.opt("signature")));
                }
                else{
                    throw new ParseErrorRPCException();
                }
                return obj;
            }catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(EcRecoverParams obj){
            try{
                JSONArray arr = new JSONArray();
                arr.put(0, DataHexStringConverter.encode(obj.dataThatWasSigned));
                                arr.put(1, DataHexStringConverter.encode(obj.signature));
                return arr.toString().replaceAll("\"","\"");
            }catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }
    }

    public static class VersionTypeConverter{
        public static VersionType decode(Object object){
            if(object==null) return null;
            return VersionType.fromString(object.toString());
        }

        public static String encode(VersionType obj){
            if(obj==null) return null;
            return obj.x;
        }
    }
}
