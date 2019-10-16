package org.aion.api.server.rpc3.types;

import org.json.JSONObject;
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

        public static String encode(Long s){
            try{
                return s.toString();
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

        public static String encode(Integer s){
            try{
                return s.toString();
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
        private final static Pattern hexPattern = Pattern.compile("^0x[0-9a-fA-F]+");
        private final static Pattern decPattern = Pattern.compile("^[0-9]+");

        public static String encodeHex(BigInteger bigInteger){
            try{
                return "0x"+bigInteger.toString();
            } catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(BigInteger bigInteger){
            try{
                return bigInteger.toString();
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

    public static class RequestConverter{
        public static Request decode(Object str){
            try{
                JSONObject jsonObject = new JSONObject(str);
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
                return jsonObject.toString();
            }
            catch (Exception e){
                throw new ParseErrorRPCException();
            }
        }

    }

    public static class DataHexStringConverter{
        private static final Pattern regex = Pattern.compile("^0x([0-9a-fA-F][0-9a-fA-F])+");

        public static String decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return StringConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(String obj){
            if (obj != null){
                String result = StringConverter.encode(obj);
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

    public static class AddressConverter{
        private static final Pattern regex = Pattern.compile(".*");

        public static String decode(Object object){
            try{
                if (object!=null && checkConstraints(object.toString())){
                    return DataHexStringConverter.decode(object);
                }
                else{
                    throw new ParseErrorRPCException();
                }
            } catch(Exception e){
                throw new ParseErrorRPCException();
            }
        }

        public static String encode(String obj){
            if (obj != null){
                String result = DataHexStringConverter.encode(obj);
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
            return regex.matcher(s).find() && s.length() >= 66 && s.length() <= 66;
        }
    }

    public static class EcRecoverParamsConverter{
        public static EcRecoverParams decode(Object object){
            String s = object.toString();
            try{
                EcRecoverParams obj;
                if(s.startsWith("[") && s.endsWith("]")){
                    JSONArray jsonArray = new JSONArray(s);
                    obj = new EcRecoverParams( StringConverter.decode(jsonArray.opt(0)), DataHexStringConverter.decode(jsonArray.opt(1)));
                }
                else if(s.startsWith("{") && s.endsWith("}")){
                    JSONObject jsonObject = new JSONObject(s);
                    obj = new EcRecoverParams( StringConverter.decode(jsonObject.opt("dataThatWasSigned")), DataHexStringConverter.decode(jsonObject.opt("signature")));
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
                arr.put(0, StringConverter.encode(obj.dataThatWasSigned));
                                arr.put(1, DataHexStringConverter.encode(obj.signature));
                return arr.toString();
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