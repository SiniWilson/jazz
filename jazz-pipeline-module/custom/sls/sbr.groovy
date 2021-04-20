package custom.sls

import common.util.Shell as ShellUtil
import common.util.Json as JSON
import common.util.Yaml as YAML
import common.util.Props as PROPS
import static common.util.Shell.sh as sh
import org.codehaus.groovy.grails.web.json.JSONObject

/*
* sbr.groovy
* This module deals with manipulation of serverless.yml in custom services
* @author: Sini Wilson
* @version: 2.0
*/

static main( args ) {
  if( args ) {
    "${args.head()}"( *args.tail() )
  }
}


/** The function traverses through the original user serverless.yml file that is represented as a Map and applies the rules from the rules file for every clause found in the user input.
    It returns a resulting Map that can immediatelly be serialized into the yml file and written to disk. config and context are also needed to resolve some values from the application yml
    @origAppYmlFile - the file in serverless serverless.yml format () as defined by a user/developer and parsed by SnakeYml (https://serverless.com/framework/docs/providers/aws/guide/serverless.yml/)
    @rulesYmlFile - the Map representation of serverless-build-rules.yml parsed by org.yaml.snakeyaml.Yaml
    @config - the Map of configs like ["service_id": "4a053679-cdd4-482a-a34b-1b83662f1e81", service: "very_cool_service", domain: "some_domain" ...]
    @context - the context values that are map like: ["INSTANCE_PREFIX": "slsapp19"]
    @return - the resulting set represents the serverless.yml in org.yaml.snakeyaml.Yaml format

    To test please uncomment the comments at the bottom of the file and provide the good values to the simple serverless.yml file and to the serverless-build-rules.yml that you can locate inside jenkins-build-sls-app project
    You can run the application in any groovy enabled environment like groovyConsole for example
*/
def Map<String, Object> processServerless(Map<String, Object> origAppYmlFile,
                                          Map<String, Object> rulesYmlFile,
                                                              config,
                                          Map<String, String> context) {


 // Loading and parsing all the rules to be presented in easily discoverable and executable form as map like [path:rule] i.e. [/service/name:SBR_Rule@127eae33, /service/awsKmsKeyArn:SBR_Rule@7e71274c, /frameworkVersion:SBR_Rule@5413e09 ...
    Map<String, SBR_Rule> rules =  convertRuleForestIntoLinearMap(rulesYmlFile)
    Map<String, SBR_Rule> resolvedRules = rulePostProcessor(rules)

    Transformer transformer = new Transformer(config, context, resolvedRules, rulesYmlFile, origAppYmlFile) // Encapsulating the config, context and rules into the class so that they do not have to be passed as an arguments with every call of recursive function
  
    Map<String, Object> transformedYmlTreelet = transformer.transform(origAppYmlFile);
    Map<String, SBR_Rule> path2MandatoryRuleMap = resolvedRules.inject([:]){acc, item -> if(item.value instanceof SBR_Rule && item.value.isMandatory) acc.put(item.key, item.value); return acc}

    Map<String, Object> mandatoryYmlTreelet = retrofitMandatoryFields(path2MandatoryRuleMap, config, context, transformer)

    Map<String, Object> ymlOutput = merge(mandatoryYmlTreelet, transformedYmlTreelet) // Order of arguments is important here because in case of collision we want the user values to overwrite the default values

    return ymlOutput
}

def Map<String, String> allRules(Map<String, Object> origAppYmlFile,
                                 Map<String, Object> rulesYmlFile,
                                                     config,
                                 Map<String, String> context) {
 // Loading and parsing all the rules to be presented in easily discoverable and executable form as map like [path:rule] i.e. [/service/name:SBR_Rule@127eae33, /service/awsKmsKeyArn:SBR_Rule@7e71274c, /frameworkVersion:SBR_Rule@5413e09 ...
    Map<String, SBR_Rule> rules =  convertRuleForestIntoLinearMap(rulesYmlFile)
    Map<String, SBR_Rule> resolvedRules = rulePostProcessor(rules)

    return  resolvedRules.inject([:]){acc, item -> acc.put(item.key, item.value.toString()); return acc;}
}

/* This class encapsulates config, context and rules so that they don't have to be carried over with every call of recursive function */
class Transformer {
  private def config;
  private Map<String, String> mandatoryFieldPaths = [:];
  private Map<String, String> context;
  private Map<String, SBR_Rule> path2RulesMap;
  private Map<String, SBR_Rule> templatedPath2RulesMap;
  private Map<String, SBR_Rule> path2MandatoryRuleMap;
  private Map<String, List> path2OrigRuleMap = [:];

  public Transformer(serviceConfig, aContext, aPath2RulesMap, aRulesYmlFile, origAppYmlFile) {
    config = serviceConfig;
    context = aContext;
    path2RulesMap = aPath2RulesMap;
    templatedPath2RulesMap = path2RulesMap.inject([:]){acc, item -> if(item.key.contains("*")) acc.put(item.key, item.value); return acc} // Copying all path-2-rule entries where a path contains '*' thus it is a template
    
    def functionTemplate = aRulesYmlFile['function']['sbr-template']
    functionTemplate.each { key, value -> 
      if (value['sbr-mandatory'] && value['sbr-mandatory'] == true) mandatoryFieldPaths["/functions/*/${key}"] = key 
    }
    def originalFunctionMap = origAppYmlFile['functions']
    originalFunctionMap.each { k, v -> 
      mandatoryFieldPaths.each  { key, value ->        
        if(path2OrigRuleMap[key] ) path2OrigRuleMap[key].add("/functions/${k}/${value}")
        else path2OrigRuleMap[key] = new HashSet(); path2OrigRuleMap[key].add("/functions/${k}/${value}")  
      } 
    }    
 }

  boolean pathMatcher(String templatedPath, String targetPath) {
    String[] templatedPathSegments = templatedPath.split("/")
    String[] targetPathSegments = targetPath.split("/")
    if(templatedPathSegments.length != targetPathSegments.length) return false
    boolean acc = true
    targetPathSegments.eachWithIndex{seg, idx -> acc = (idx == 0 || seg.equals(templatedPathSegments[idx]) ||  templatedPathSegments[idx].contains("*")) & acc}
    return acc
  }

  List<String> resolveAsterisks(String templatedPath, String targetPath) {
    List<String> val2Ret = []
    if(!templatedPath.contains("*")) return val2Ret

    String[] templatedPathSegments = templatedPath.split("/")
    String[] targetPathSegments = targetPath.split("/")

    targetPathSegments.eachWithIndex{seg, idx -> if(templatedPathSegments[idx] == "*") val2Ret.add(targetPathSegments[idx])}

    if(path2OrigRuleMap[(templatedPath)] ) path2OrigRuleMap[(templatedPath)].add(targetPath)
    else path2OrigRuleMap[(templatedPath)] = new HashSet(); path2OrigRuleMap[(templatedPath)].add(targetPath)
          
    return val2Ret
  }


  private SBR_Rule ruleMatcher(aPath) {
    SBR_Rule simpleMatch = path2RulesMap[aPath]
    if(simpleMatch != null) {
      return simpleMatch;
    } else {
      def path2rule = templatedPath2RulesMap.find{thePath2Rule -> pathMatcher(thePath2Rule.key, aPath)}
      if(path2rule == null) return null
      path2rule.value.asteriskValues = resolveAsterisks(path2rule.key, aPath)
      return path2rule.value
    }
  }

  private def processor(aSubTree, currentPath) {
    SBR_Rule theRule = ruleMatcher(currentPath);
    if(theRule != null) {
     return theRule.applyRule(aSubTree, currentPath, config, context)
    } else {
      if(aSubTree instanceof Map) return aSubTree.inject([:]){acc, item -> acc.put(item.key, processor(item.value, currentPath+"/"+item.key) ); return acc}
      else if(aSubTree instanceof List) return aSubTree.collect{item -> processor(item, currentPath+"/*") }    
      else throw new IllegalStateException("Your application definition - serverless.yml contains a path `${currentPath}` that is not supported. Please refer to documentation for supported paths.")
    }
  }


  public def transform(Map<String, Object> originalServerless) {
    Map<String, Object> ymlOutput = processor(originalServerless, "")
    return ymlOutput
  }

}

/* The interface to generailize all type validations */
interface TypeValidator {
  void isValid(def aValue)
}

/*The simples example of type validation all others must be repeated after this example */
class IntValidator implements TypeValidator {
  public void isValid(def aValue) {
    try {
      Integer.parseInt(aValue)
    } catch(e) {
      throw new IllegalArgumentException("Invalid Integer: " + aValue + " is of type: " + aValue?.class);
    }
  }
}

class StringValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof String)
    throw new IllegalArgumentException("Invalid String: " + aValue + " is of type: " + aValue?.class);
  }
}

class BooleanValidator implements TypeValidator {
  public void isValid(def aValue) {
    try {
      Boolean.parseBoolean(aValue)
    } catch(e) {
      throw new IllegalArgumentException("Invalid Boolean: " + aValue + " is of type: " + aValue?.class);
    }
  }
}

class EnumValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof Enum)
    throw new IllegalArgumentException("Invalid Enum: " + aValue );
  }
}

class ListValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof List)
     throw new IllegalArgumentException("Invalid List: " + aValue + " is of type: " + aValue?.class);
  }
}

class MapValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof Map)
     throw new IllegalArgumentException("Invalid Map: " + aValue + " is of type: " + aValue?.class);
  }
}

class JsonValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof JSONObject)
      throw new IllegalArgumentException("Invalid Json: " + aValue + " is of type: " + aValue?.class);
  }
}

class DeploymentStageNameValidator implements TypeValidator {
  public void isValid(def aValue) {
    // if(!(aValue == "prod" || aValue == "stg" || aValue.endsWith("dev")))
    // throw new IllegalArgumentException("Invalid stage : " + aValue );
  }
}

class SequenceValidator implements TypeValidator {
  public void isValid(def aValue) {
    if(!aValue instanceof List)
    throw new IllegalArgumentException("Invalid Sequence: " + aValue + " is of type: " + aValue?.class);
  }
}

class IamArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:iam::\\d{12}:role/?[a-zA-Z_0-9+=,.@\\-_/]+"
    def match = aValue ==~ pattern
    if(!match)
     throw new IllegalArgumentException("Invalid IAM Arn: " + aValue );
  }
}

class KmsArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:kms::\\d{12}:key/?[a-zA-Z_0-9+=,.@\\-_/]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid KMS Arn: " + aValue );
  }
}

class AwsIdValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^\\d{12}"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid AWS ID: " + aValue );
  }
}

class FunctionValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z_0-9+=,.@\\-_/]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Function: " + aValue );
  }
}

class PluginValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z0-9_.-]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Plugin :" + aValue)
  }
}

class ResourceValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Resource: " + aValue );
  }
}

class PolicyValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Policy Arn: " + aValue );
  }
}

class EventValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Event: " + aValue );
  }
}

class AwsVariableValueValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Aws variable value: " + aValue );
  }
}

class AwsVariableNameValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Aws variable name: " + aValue );
  }
}


class GenericArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def elements = aValue.split(":")
    if(elements.size() != 6)
    throw new IllegalArgumentException("Invalid Arn: " + aValue );
  }
}

class SnsArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:sns::\\d{12}:?[a-zA-Z_0-9+=,.@\\-_/]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid SNS Arn: " + aValue );
  }
}

class LayerArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:opsworks::\\d{12}:layer/?[a-zA-Z_0-9+=,.@\\-_/]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Layer Arn: " + aValue );
  }
}

class LambdaArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:lambda::\\d{12}:function:?[a-zA-Z0-9-_]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid SQS Arn: " + aValue );
  }
}

class SqsArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:sqs::\\d{12}:?[a-zA-Z0-9-_]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid SQS Arn: " + aValue );
  }
}

class IamPolicyArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:iam::\\d{12}:([user|group]+)\\/\\*"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Iam policy Arn: " + aValue );
  }
}

class KinesisArnValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^arn:aws:kinesis::\\d{12}:stream/?^[a-zA-Z0-9_.-]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Kinesis Arn: " + aValue );
  }
}

class AwsArtifactNameValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "[a-zA-Z0-9_\\-]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid artifact name: " + aValue );
  }
}

class AwsS3BucketNameValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "(?=^.{3,63})(?!^(\\d+\\.)+\\d+)(^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9]))"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid S3 Bucket name: " + aValue );
  }
}

class AwsTagNameValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z_0-9+=,.@\\-_/+-=._:/ ]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid Tag name: " + aValue );
  }
}

class AwsScheduleRateValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "(cron|rate)?([()\\d\\?*, ]+)"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid schedule rate expression: " + aValue );
  }
}

class AwsPathValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z_0-9+.\\-_/. ]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid AWS path: " + aValue );
  }
}

class AwsPrincipleValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z_0-9+.\\-_/.*? ]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid AWS principle: " + aValue );
  }
}

class AwsDescriptionValidator implements TypeValidator {
  public void isValid(def aValue) {
    def pattern = "^[a-zA-Z_0-9+=,.@\\-_/+-=._:/ ]+"
    def match = aValue ==~ pattern
    if(!match)
    throw new IllegalArgumentException("Invalid AWS description: " + aValue );
  }
}

/* Enum that must enlist all the types from serverless-build-rules.yml file. TODO: The lists and maps must be dealt with properly */
enum SBR_Type {

   INT("int", new IntValidator()),
   BOOL("bool", new BooleanValidator()),
   STR("str", new StringValidator()),
   ENUM("enum", new EnumValidator()),
   JSON("json", new JsonValidator()),

   ARN("arn", new GenericArnValidator()),
   ARN_KMS("arn-kms", new KmsArnValidator()),
   ARN_IAM("arn-iam", new IamArnValidator()),
   AWS_ID("aws-id", new AwsIdValidator()),
   ARN_SNS("arn-sns", new SnsArnValidator()),
   ARN_LAYER("arn-layer", new LayerArnValidator()),
   ARN_LAMBDA("arn-lambda", new LambdaArnValidator()),
   ARN_SQS("arn-sqs", new SqsArnValidator()),
   ARN_IAM_POLICY("arn-iam-policy", new IamPolicyArnValidator()), // TODO Must provide a validator
   ARN_KINESIS("arn-kinesis", new KinesisArnValidator()),
   AWS_ARTIFACT_NAME("aws-artifact-name", new AwsArtifactNameValidator()),
   AWS_BUCKET_NAME("aws-bucket-name", new AwsS3BucketNameValidator()),
   AWS_TAG_VAL("aws-tag-value", new AwsTagNameValidator()),
   AWS_SCHEDULE_RATE("aws-schedule-rate", new AwsScheduleRateValidator()),
   PATH("path", new AwsPathValidator()),
   AWS_PRINCIPAL("aws-principal", new AwsPrincipleValidator()),
   AWS_DESCRIPTION("aws-description", new AwsDescriptionValidator()),
   AWS_VAR_VALUE("aws-var-value", new AwsVariableValueValidator()),
   AWS_VAR_NAME("aws-var-name", new AwsVariableNameValidator()),
   FUNCTION("function", new FunctionValidator()),
   PLUGIN("plugin", new PluginValidator()),
   EVENT("event", new EventValidator()),
   RESOURCE("resource", new ResourceValidator()),
   AWS_POLICY("aws-policy",  new PolicyValidator()), // TODO Must provide a validator
   DEPLOYMENT_STAGE("deployment-stage", new DeploymentStageNameValidator()),
   MAP("[:]", new MapValidator()),
   LIST("[]", new ListValidator()),
   SEQUENCE("sequence", new SequenceValidator())    // TODO Must provide a validator




   String tagValue
   TypeValidator typeValidator

   public SBR_Type(aTagValue, aValidator) {
     tagValue = aTagValue
     typeValidator = aValidator
   }

   public void validate(aValue) {
     if(typeValidator != null) typeValidator.isValid(aValue)
// TODO We have to implement all the validators and then re-instate the following statement here: "else throw new IllegalStateException("No validator is not set for: $tagValue")"
   }

   static final SBR_Type getByTagValue(String aTagValue) {
     if(aTagValue.contains("[")) {
       if(aTagValue.contains(":")) return MAP
       else return LIST
     }

     Map<String, SBR_Type> tagVal2TypeMap =  SBR_Type.values() // Lists all type enum values declared above
                                                     .collect{aType -> [(aType.tagValue) : aType]} // Making alist of maplets to persist both tagValue and the encompassing type together as a some form of tuple [ ["int":INT], ["bool":BOOL], ...]
                                                     .inject([:]){acc, item -> item.each{entry -> acc.put(entry.key, entry.value)}; return acc} // Transforming the list of maplets into one convenient map that help us to resolve the type by tagValue provided ["int":INT, "bool":BOOL, ..., "aws_bucket_name": AWS_BUCKET_NAME, ...]

     SBR_Type theType = tagVal2TypeMap[aTagValue]
     if(theType == null) throw new IllegalArgumentException("[SBR_Type] Unknown tagValue: "+aTagValue)

     return theType

   }

}

// In case the type is a map or list we have to preserve the argument types inside the list or map
class SBR_Type_Descriptor {
  SBR_Type type
  List<SBR_Type> underlyingTypeList

  public SBR_Type_Descriptor(aType, anUnderlyingTypeList) {
    type = aType
    underlyingTypeList = anUnderlyingTypeList
  }

  public boolean isMap() {
    return type == SBR_Type.MAP
  }

  public boolean isList() {
    return type == SBR_Type.LIST
  }

  public SBR_Type getType() {
    return type
  }

  public List<SBR_Type> getUnderlyingTypeList() {
    return underlyingTypeList
  }

  static final SBR_Type_Descriptor parseTag(aTag) {
     String typeExtracted = aTag["sbr-type"]
     SBR_Type type = SBR_Type.getByTagValue(typeExtracted);
     switch(type) {
       case SBR_Type.LIST:
         String underlyingTypeAsString = typeExtracted.replace("[","").replace("]","")
         SBR_Type underlyingType = SBR_Type.getByTagValue(underlyingTypeAsString)
         return new SBR_Type_Descriptor(type, [underlyingType]);
       case SBR_Type.MAP:
         String twoUnderlyingTypesAsString = typeExtracted.replace("[","").replace("]","")
         String[] underlyingTypesAsString = twoUnderlyingTypesAsString.split(":")
         return new SBR_Type_Descriptor(type, [SBR_Type.getByTagValue(underlyingTypesAsString[0]), SBR_Type.getByTagValue(underlyingTypesAsString[1])]);
       default: return new SBR_Type_Descriptor(type, [])
     }
  }

  String toString() {
    return "SBR_Type_Descriptor{type:"+type+"; underlyingTypeList="+underlyingTypeList+"}"
  }

  public void validate(aValue) {
    if (!underlyingTypeList.contains(aValue))
    {
      // TODO for now we are not throwing this exception as the validation implementation is incomplete
      // throw new IllegalStateException("The following type is not supported: $aValue Supported types are: ${underlyingTypeList}")
    }
  }
}

/* Resolves value in accordance with render policy. */
interface Resolver {
  Object resolve(Object userVal, Object configVal, Object defaultValue)
}

class UserWinsResolver implements Resolver {
  public Object resolve(Object userVal, Object configVal, Object defaultValue) {
     // TODO: Put the log entry with both user and config values here
     // TODO: formula evaluation needs to be fixed
     // since formula evaluation always returns a String we need to handle that here
     if (configVal != null && configVal.toString().equals('null'))
     {
       configVal = null
     }
     return userVal ? userVal : configVal ? configVal : defaultValue
  }
}

class UserOnlyResolver implements Resolver {
  public Object resolve(Object userVal, Object configVal, Object defaultValue) {
    return userVal ? userVal : defaultValue
  }
}

class ConfigWinsResolver implements Resolver {
  public Object resolve(Object userVal, Object configVal, Object defaultValue) {
// TODO: Put the log entry with both user and config values here
    return configVal
  }
}

class ConfigOnlyResolver implements Resolver {
  public Object resolve(Object userVal, Object configVal, Object defaultValue) {
    return configVal
  }
}

class ConfigMerge implements Resolver {
  public Object resolve(Object userVal, Object configVal, Object defaultValue) {

    if(userVal instanceof List &&  configVal instanceof List) {
      def out = []
      if(userVal != null) out += userVal
      if(configVal != null) out += configVal
      return out.unique()
    } else if(userVal instanceof Map &&  configVal instanceof Map) {
      def out = [:]
      if(userVal != null) out << userVal
      if(configVal != null) out << configVal
      return out
    } else if(userVal instanceof String &&  configVal instanceof String) {
      return userVal+"-"+configVal
    } else {
      throw new IllegalStateException("Type mismatch. UserVal Class = "+userVal.getClass().getName()+"; "+
                                      "configVal Class= "+configVal.getClass().getName())
    }
  }
}

enum SBR_Render {
  USER_WINS("user-wins", new UserWinsResolver()),
  CONFIG_WINS("config-wins", new ConfigWinsResolver()),
  USER_ONLY("user-only", new UserOnlyResolver()),
  CONFIG_ONLY("config-only", new ConfigOnlyResolver()),
  EXCEPTION_ON_MISMATCH("exception-on-mismatch", null), // TODO: Write a resolver
  MERGE("merge", new ConfigMerge()) // TODO: Write a resolver

  private String tagValue
  private Resolver resolver

  public SBR_Render(aTagValue, aResolver) {
    tagValue = aTagValue
    resolver = aResolver
  }

  public Object resolve(userVal, configVal, defaultValue) {
    if(resolver != null) return resolver.resolve(userVal, configVal, defaultValue)
    else throw new IllegalStateException("The resolver is not set for $tagValue")
  }

  static final SBR_Render getByTagValue(aTagValue) {

    Map<String, SBR_Render> tagVal2RenderMap =  SBR_Render.values()
                                                     .collect{aType -> [(aType.tagValue) : aType]}
                                                     .inject([:]){acc, item -> item.each{entry -> acc.put(entry.key, entry.value)}; return acc}

     SBR_Render theRender = tagVal2RenderMap[aTagValue]
     if(theRender == null) throw new IllegalArgumentException("[SBR_Render] Unknown tagValue: "+aTagValue)

     return theRender
  }

}

// Generalizes all constraints
interface SBR_Constraint {
  boolean compliant(val)
}

class SBR_Composite_Constraint implements SBR_Constraint {
  private List<SBR_Constraint> constraintList = new ArrayList<>();

  static final SBR_Constraint parseTag(tag) {
    SBR_Constraint cumulativeConstr = new SBR_Composite_Constraint();
    tag.collect{key, value ->
      switch(key) {
        case "sbr-enum": cumulativeConstr.constraintList.add(new SBR_Enum_Constraint(value)); break;
        case "sbr-from": cumulativeConstr.constraintList.add(new SBR_From_Constraint(value)); break;
        case "sbr-to": cumulativeConstr.constraintList.add(new SBR_To_Constraint(value)); break;
        case "sbr-whitelist": cumulativeConstr.constraintList.add(new SBR_Whitelist_Constraint(value)); break; // TODO real whitelist loaded needed here instead of an empty map
        default: throw new IllegalStateException("sbr-constraint contains an unknown tag inside as follows: $key")
      }
    }

    return cumulativeConstr
  }

  public boolean compliant(val) {
    return !constraintList.any{elem -> !elem.compliant(val)};
  }
}

class SBR_Enum_Constraint implements SBR_Constraint {
  private ArrayList<String> enumValue = new ArrayList<String>();

  public SBR_Enum_Constraint(inputEnum) {
    enumValue.addAll(inputEnum)
  }

  public boolean compliant(aVal) {
    if(aVal == null || aVal == "") {
      return false
    } else {
      def status = enumValue.find{val -> val == aVal.toString()}
      return status ? true: false
    }
  }
}

class SBR_Whitelist_Constraint implements SBR_Constraint {
  private String elementPointer
  private def whitelistValidator = new WhiteListValidatorModule()

   public SBR_Whitelist_Constraint(anElementPointer) {
     elementPointer = anElementPointer
  }

  public boolean compliant(val) {
    switch(elementPointer) {
      case "resources": return whitelistValidator.validateWhitelistResources(val); break;
      case "events": return whitelistValidator.validateWhitelistEvents(val); break;
      case "plugins": return whitelistValidator.validateWhitelistPlugins(val); break;
      case "actions": return whitelistValidator.validateWhitelistActions(val); break;
      case "iamManagedPolicies": return whitelistValidator.validateWhitelistIamManagedPolicies(val); break;
      default: throw new IllegalStateException("SBR_Whitelist_Constraint contains an unknown $elementPointer inside as follows: $val")
    }
  }
}

class SBR_To_Constraint implements SBR_Constraint {
  private int toValue

  public SBR_To_Constraint(int aToValue) {
    toValue = aToValue;
  }

  public boolean compliant(val) {
    try {
      if(val && val != '')  return Integer.parseInt(val.toString()) <= Integer.parseInt(toValue.toString());
      else return false
    } catch(e) {
      throw new IllegalArgumentException("Invalid Integer: " + val + " is of type: " + val?.class);
    }
  }
}

class SBR_From_Constraint implements SBR_Constraint {
  private int fromValue

  public SBR_From_Constraint(int aFromValue) {
    fromValue = aFromValue
  }

  public boolean compliant(val) {
    try {
      if(val && val != '')  return Integer.parseInt(val.toString()) >= Integer.parseInt(fromValue.toString());
      else return false
    } catch(e) {
      throw new IllegalArgumentException("Invalid Integer: " + val + " is of type: " + val?.class);
    }
  }
}

// Encapsulates formulas and default values
interface SBR_Value {
  Object renderValue(config, context, asterisks)
}

class SBR_Example_Value implements SBR_Value {
  private def value;

  public SBR_Example_Value(aValue) {
    value = aValue;
  }

  public renderValue(config, context, asterisks) {
    return value;
  }
}

class SBR_Formula_Value implements SBR_Value {
  private def formula

  static final SBR_Value parseTag(aValueTag) {
    if(aValueTag["sbr-formula"] != null) {
      return new SBR_Formula_Value(aValueTag["sbr-formula"])
    } else {
      throw new IllegalStateException("The formula is expected under the value tag for now")
    }
  }

  public SBR_Formula_Value(aFormula) {
    formula = aFormula
  }

  public def renderValue(serviceConfig, aContext, aAsteriskList) {
    def sharedData = new Binding()
    def shell = new GroovyShell(sharedData)

    sharedData.setProperty('config', serviceConfig)
    sharedData.setProperty('context', aContext)
    aAsteriskList.eachWithIndex{item, idx -> sharedData.setProperty("asterisk$idx", item)}

    def result

    if(formula instanceof String) {
      result = formula.startsWith("_") ? formula.replace("_", "") : shell.evaluate('"'+formula+'"') // TODO Investigate what to do with an exception here
    } else if(formula instanceof Map) {
      result = formula.inject([:]){acc, item -> acc.put(item.key, shell.evaluate('"'+item.value+'"')); return acc}
    } else if(formula instanceof List) {
      def res = formula.collect{item -> shell.evaluate('"'+item+'"')}
      result = res[0].trim().replaceAll(~/^\[|\]$/, '').split(',').collect{ it.trim()}
    } else {
      throw new IllegalStateException("The formula is of unknown type "+formula.getClass().getName())
    }
    
    return result;
  }


  public String toString() {
    return "formula: $formula" ;
  }
}

interface SBR_Template {
  Map<String, SBR_Rule> getPath2RuleMap()
}

class SBR_PreRule {
   SBR_Type_Descriptor type
   SBR_Render render
   SBR_Constraint constraint

   public SBR_PreRule(SBR_Type_Descriptor aType,
                   SBR_Render aRender,
                   SBR_Constraint aConstraint) {
     type = aType
     render = aRender
     constraint = aConstraint
   }

}

class SBR_Rule extends SBR_PreRule {
   SBR_Value value
   boolean isMandatory
   SBR_Example_Value defaultValue
   List<String> asteriskValues = []

   public SBR_Rule(SBR_Type_Descriptor aType,
                   SBR_Render aRender,
                   SBR_Constraint aConstraint,
                   SBR_Value aValue,
                   boolean aIsMandatory,
                   SBR_Example_Value aDefaultValue) {
      super(aType, aRender, aConstraint)
      isMandatory = aIsMandatory
      value = aValue
      defaultValue = aDefaultValue
   }

   public Object applyRule(userValue, path, config, context) {

     def valueRendered = (value != null) ? value.renderValue(config, context, asteriskValues) : userValue;
     def defValue = (defaultValue != null) ? defaultValue.renderValue(config, context, asteriskValues) : ""
     def theValue = render.resolve(userValue, valueRendered, defValue)

     type.validate(theValue); // This will raise the exception if type is wrong but we shave to suppliment it with path so TODO is to catch the exceotion then add the path and the re-throw it

     if(constraint != null && !constraint.compliant(theValue)) {
       throw new IllegalStateException("Your application definition - serverless.yml contains value `${theValue}` for `${path}` that violates one of our rules. Please refer to documentation for valid values for `${path}`.")    
     }
     return theValue
   }

   public String toString() {
     return "SBR_Rule {type: $type, render: $render, value: $value, isMandatory: $isMandatory}\n";
   }
}

class SBR_NonPrimaryRule extends SBR_PreRule {
  def template

  public SBR_NonPrimaryRule(SBR_Type_Descriptor aType,
                            SBR_Render aRender,
                            SBR_Constraint aConstraint,
                            aTemplate) {
      super(aType, aRender, aConstraint)
      template = aTemplate
  }

  public Map<String, SBR_Rule> getLinearRuleMap() {
    return convertRuleForestIntoLinearMap(template)
  }

  public String toString() {
    String templateClass = template.getClass().getName()
    return "SBR_NonPrimaryRule {type: $type, render: $render, templateClass: $templateClass}\n"
  }
}

// Those below are stray functions now TODO: May need to move them into a separate class to encapsulate
boolean isLeaf(Object aTag) {
  return aTag instanceof Map && aTag.get("sbr-type") != null
}

def extractLeaf(Map<String, Object> aTag) {
  SBR_Type_Descriptor type = SBR_Type_Descriptor.parseTag(aTag);
  SBR_Render render = SBR_Render.getByTagValue(aTag["sbr-render"]);
  SBR_Constraint constraint = null;
  def constraintTag = aTag["sbr-constraint"]
  if(constraintTag != null) {
    constraint = SBR_Composite_Constraint.parseTag(constraintTag)
  }

  SBR_Value value = null;
  SBR_Example_Value defaultValue
  def valueTag = aTag["sbr-value"]
  if(valueTag != null) {
    if(valueTag["sbr-formula"] != null) value = SBR_Formula_Value.parseTag(valueTag) // Only Formula tag is implemented for now this if shall go away eventully
    if(valueTag["sbr-example"] != null) defaultValue = new SBR_Example_Value(valueTag["sbr-example"])
  }

  boolean primary = (aTag["sbr-primary"] != null && !aTag["sbr-primary"]) ? false : true

  boolean isMandatory = (aTag["sbr-mandatory"] == true) ? true : false

  SBR_PreRule retVal = primary ? new SBR_Rule(type, render, constraint, value, isMandatory, defaultValue) : new SBR_NonPrimaryRule(type, render, constraint, aTag["sbr-template"])

  return retVal;

}

def collector(ruleTree, currentPath) {
  if(isLeaf(ruleTree)) return [(new String(currentPath)) : extractLeaf(ruleTree)]

  if(ruleTree instanceof Map) return ruleTree.collect{key, val -> collector(val, currentPath+"/"+key)}
  else {return ruleTree.collect{val -> collector(val, currentPath)}}
}

/* Convering a map of maps of maps into a united map of 'path to rule' relations like
["/service/name": SBR_Rule {type: SBR_Type_Descriptor{type:AWS_ARTIFACT_NAME; underlyingTypeList=[]}, render: CONFIG_ONLY, isPrimary: true, value: formula: ${props.configData.INSTANCE_PREFIX}-${config.service}},
 "/service/awsKmsKeyArn": SBR_Rule {type: SBR_Type_Descriptor{type:ARN_KMS; underlyingTypeList=[]}, render: USER_ONLY, isPrimary: true, value: null},
 "/frameworkVersion": SBR_Rule {type: SBR_Type_Descriptor{type:STR; underlyingTypeList=[]}, render: USER_ONLY, isPrimary: true, value: null},
          ................
]
*/
Map<String, SBR_Rule> convertRuleForestIntoLinearMap(/* Map<String, Object> */  ruleForest) {
 // Loading and parsing all the rules to be presented in easily discoverable and executable form as map like [path:rule] i.e. [/service/name:SBR_Rule@127eae33, /service/awsKmsKeyArn:SBR_Rule@7e71274c, /frameworkVersion:SBR_Rule@5413e09 ...

    Map<String, SBR_Rule> path2RuleMap =  collector(ruleForest, "") // collector is the function that will return the map of enclosed map due to it rucursive nature
                                                                     .flatten() // so flatten is needed to convert this tree like structure into the map like [[/service/name:SBR_Rule@127eae33], [/service/awsKmsKeyArn:SBR_Rule@7e71274c], ...]
                                                                     .inject([:]){acc, item -> item.each{entry -> acc.put(entry.key, entry.value)};  return acc} // Now the reduce step is needed to convert all the sub-maplets into one big convenient map

  return path2RuleMap
}

def extractRefs(Map<String, SBR_Rule> aPath2RuleMap, Map<String, SBR_Rule> nonPrimaryRules) {
  def ret = aPath2RuleMap.inject([:]){acc, item -> def npr = resolveReferencedRule(item.value, nonPrimaryRules); if(npr != null) acc.put(item.key, item.value); return acc}
  return ret
}


def extractNonPrimary(Map<String, SBR_Rule> aPath2RuleMap) {
  def ret = aPath2RuleMap.inject([:]){acc, item -> if(item.value instanceof SBR_NonPrimaryRule ) acc.put(item.key, item.value); return acc}
  return ret
}

Map<String, SBR_Rule> explodeNonPrimaryRule(aRule, String prefix) {
  return convertRuleForestIntoLinearMap(aRule.template).inject([:]){acc, item -> acc.put(prefix+"/"+"*"+item.key, item.value); return acc}
}

SBR_NonPrimaryRule resolveReferencedRule(SBR_PreRule aReferrerRule, Map<String, SBR_NonPrimaryRule> nonPrimaryRulesMap) {
  SBR_Type_Descriptor type = aReferrerRule.type;
  if(type.isMap()) {
    String correspondingTagType = type.underlyingTypeList[1].tagValue
    return nonPrimaryRulesMap["/"+correspondingTagType]
  } else if(type.isList()) {
    String correspondingTagType = type.underlyingTypeList[0].tagValue
    return nonPrimaryRulesMap["/"+correspondingTagType]
  } else {
    return null
  }
}

// Rules that do not have its own value but instead in its type (that can be a MAP or LIST) it contains the reference to non-primary rule
Map<String, SBR_Rule> resolveReferences(aReferenceRules, nonPrimaryRules) {
  def theRules = aReferenceRules.inject([:]){acc, item -> acc.put(item.key, resolveReferencedRule(item.value, nonPrimaryRules)); return acc}
                                .inject([:]){acc, item -> def expl = explodeNonPrimaryRule(item.value, item.key); expl.each{entry -> acc.put(entry.key, entry.value)}; return acc}

  return theRules
}


/* Resolves all the type based references that ties together an SBR_Rule that is called 'Referrer' and non-primary rule that we call 'Resolved'.
   The resolved set is added to the original input map while the Referrers and non-primary elements are deleted from the input */
Map<String, SBR_Rule> rulePostProcessor(Map<String, SBR_PreRule> aPath2RuleMap) {
  Map<String, SBR_NonPrimaryRule> path2NonPrimaryRuleMap = extractNonPrimary(aPath2RuleMap) // Finding all non-primary rules
  Map<String, SBR_Rule> path2ReferrerRuleMap = extractRefs(aPath2RuleMap, path2NonPrimaryRuleMap) // Finding all Referrers that address the associated non-primary rule
  Map<String, SBR_Rule> path2ResolvedRuleMap = resolveReferences(path2ReferrerRuleMap, path2NonPrimaryRuleMap) //Replacing all the referrer parts with the content from non

  // Repeating the excersise here as I know that the  path2ResolvedRuleMap itself will still contain the rules to be resolver over again. Ideally it should have been done in the loop that continues the process until no resolutions has occured
  Map<String, SBR_Rule> path2ReferrerRuleMapFinal = extractRefs(path2ResolvedRuleMap, path2NonPrimaryRuleMap)
  Map<String, SBR_Rule> path2ResolvedRuleMapFinal = resolveReferences(path2ReferrerRuleMapFinal, path2NonPrimaryRuleMap)

  if(path2ResolvedRuleMap != null) path2ResolvedRuleMapFinal << path2ResolvedRuleMap // Concat two resulting maps

  Map<String, SBR_PreRule> path2RuleMap2Ret = subtract(aPath2RuleMap,
                                                       path2NonPrimaryRuleMap,
                                                       path2ReferrerRuleMap,
                                                       path2ReferrerRuleMapFinal) // aPath2RuleMap - path2NonPrimaryRuleMap - path2ReferrerRuleMap - path2ReferrerRuleMapFinal

  if(path2ResolvedRuleMapFinal != null) path2RuleMap2Ret << path2ResolvedRuleMapFinal // Adding all resolved maps to the original input

  return path2RuleMap2Ret
}

def subtract(target, arg1, arg2, arg3) {
  return target.inject([:]){acc, item -> if(arg1[item.key] == null && arg2[item.key] == null && arg3[item.key] == null) acc.put(item.key, item.value); return acc}
}

/* Merges two maps nicely. In case of conflict the second (later) argument overwrites the first (early) one  */
def Map merge(Map[] sources) {
    if (sources.length == 0) return [:]
    if (sources.length == 1) return sources[0]

    sources.inject([:]) { result, source ->
      source.each { k, v ->
          result[k] = (result[k] instanceof Map && v instanceof Map ) ?  merge(result[k], v) : v
      }
      return result
    }
}


/* Converting Array to List that is a much nicer to work with */
def toList(value) {
    [value].flatten().findAll { it != null }
}

/* Creates a new map and adds it to the envelopeMap as the new entry under the given key */
def enclose(Map envelopeMap, String key) {
  if(key.isEmpty()) return envelopeMap
  def Map enclosedContent = [:]
  envelopeMap[key] = enclosedContent
  return enclosedContent
}

/* Returns a new yml treelet with a single path which the rule result is placed under (at) */
def retrofitMandatoryFields(String              aPath,
                            SBR_Rule            rule,
                                                config,
                            Map<String, String> context,
                            Transformer transformer) {

  Map<String, Object> ymlTree = [:]
  String[] segmentedPath = aPath.split("/")

  List<String> pathAsList = toList(segmentedPath)
// The Jenkins groovy does not support removeLast so I had to substitute it with two following lines
  String lastName = pathAsList[pathAsList.size()-1]
  pathAsList.removeAt(pathAsList.size()-1)
  def lastHandler =  pathAsList.inject(ymlTree){acc, item -> enclose(acc, item)}

  def userDefaultValue = ""
  if(rule.type.isMap()) userDefaultValue = [:]
  if(rule.type.isList()) userDefaultValue = []
  def origRule = transformer.ruleMatcher(aPath)
  if (origRule) {
    rule.asteriskValues = origRule.asteriskValues
  }
  lastHandler[lastName] = rule.applyRule(userDefaultValue, aPath, config, context)

  return ymlTree
}

def makeList(list) {
    List created = new ArrayList()
    list.each { line ->
        created.add(line)
    }
    return created
}

def getLeafPath (String templatedPath, Map<String, List> path2OrigRuleMap) {
  def pathKeyArr = makeList(templatedPath.split('/'))
  def maxSizeMap = path2OrigRuleMap.findAll { entry -> entry.key.split('/').size() == pathKeyArr.size() }
  def maxList = maxSizeMap.collect{ it.value.size() }
  def maxSize = maxList.max { it -> it.value }
  def pathTempKeyList = path2OrigRuleMap.find {it -> it.value.size() == maxSize}                                       
  return pathTempKeyList ? pathTempKeyList.value: []
}


def findTargetPath (String templatedPath, Map<String, List> path2OrigRuleMap) {
  def pathKey = path2OrigRuleMap.keySet().find { templatedPath.contains(it)  }
  def targetedPaths = pathKey ? path2OrigRuleMap.get(pathKey) : getLeafPath(templatedPath, path2OrigRuleMap)

  def pathKeyArr = makeList(templatedPath.split('/'))
  def asteriskIdx = pathKeyArr.findIndexOf{it =="*"}

  List path2OrigKey = new ArrayList()
  for (path in targetedPaths) {
    def pathArr = makeList(path.split('/'))
    def pathValue = pathArr[asteriskIdx]
    pathKeyArr[asteriskIdx] = pathValue
    def reqPath = pathKeyArr.join("/")
    if(reqPath.contains("*")) {
      def commons = pathKeyArr.intersect(pathArr)
      reqPath = commons.join("/")
      def difference = pathKeyArr.plus(pathArr)
      difference.removeAll(commons)
      difference.removeAll("*")
      def diff = difference.join("/")
      reqPath = "${reqPath}/${diff}"
    }
    path2OrigKey.add(reqPath)
  }

  return path2OrigKey
}


/* Returning a new yml tree with paths enlisted inside the map and with associated rule result placed under */
def retrofitMandatoryFields(Map<String, SBR_Rule> aPath2RuleMap,
                                                  config,
                            Map<String, String>   context,
                            Transformer transformer) {

  Map<String, List> path2OrigRuleMap = transformer.path2OrigRuleMap
  def accumulator = aPath2RuleMap.inject([:]){acc, item ->
    def targetedPaths = new ArrayList()
    if((item.key).toString().contains("*")) targetedPaths = findTargetPath (item.key, path2OrigRuleMap)
    else targetedPaths.add(item.key)

    targetedPaths.each { entry ->
      def ymlTreelet = retrofitMandatoryFields(entry, item.value, config, context, transformer)
      def accCopy = [:]; if(acc != null) accCopy << acc;
      acc  = merge(accCopy, ymlTreelet);
    }
    return acc;
  }
  return accumulator
}

/**
* Function for checking https events
*/
def checkForHttpEvents(doc){
  def result = false;
  def value;
  if(doc.functions){
    def functionKeys = doc.functions.keySet();
    if(functionKeys.size() > 0){
      for(functionKey in functionKeys) {
        // checking if atleast one of the events has an http key functionKey, if yes then breaking out of the loop
          if(doc.functions[functionKey] && doc.functions[functionKey].events){
            def keyMapList = [:]
            for (def i in doc.functions[functionKey].events){
                keyMapList.putAll(i)
            }
            value = keyMapList.containsKey('http')
            if(value){
              result = true
              break;
            }
          }
      }
    }
  }
  return result
}

/**
* Function for checking httpApi events
*/
def checkForHttpApiEvents(doc){
  if(doc.provider && doc.provider.httpApi) {
    if(doc.provider.httpApi.authorizers) {
      def authorizers = doc.provider.httpApi.authorizers.keySet();
      println "provider authorizers: $authorizers"
      if (doc.functions) {
        def functionKeys = doc.functions.keySet();
        // iterating all functions
        for(functionKey in functionKeys) {
          // checking if atleast one of the events has an http key functionKey, if yes then checking if authorizer is defined in provider
          if(doc.functions[functionKey] && doc.functions[functionKey].events && doc.functions[functionKey].events.httpApi){
            println "httpApi event is provided."
            def keyMapList = doc.functions[functionKey].events.httpApi;
            if(keyMapList.authorizer && keyMapList.authorizer.name) {
              println "event Authorizer: $keyMapList.authorizer.name"
              def functionEventAuth = keyMapList.authorizer.name;
              if (authorizers.contains(functionEventAuth)) {
                println "Validated authorizer for httpApi event."
              } else {
                println "Authorizer mismatch: Authorizer used in function is not defined in provider: " + functionEventAuth
                throw new Exception ("Your application definition - serverless.yml has Authorizer mismatch error: Authorizer used in function is not defined in provider: " + functionEventAuth);
              }
            } else {
              println "No authorizer defined in httpApi event."
              throw new Exception ("Your application definition - serverless.yml has No authorizer defined httpApi event.");
            }
          }
          
        } 
      }
    } else {
        println "No authorizer found in httpApi provider."
        throw new Exception ("Your application definition - serverless.yml has No authorizer for httpApi provider.");
    }
  } else {
      println "httpApi provider is not added."
  }
}

/**
* Function to remove underscore from function name in user serverless.yml 
*/
def removeUnderscoreInFunctionName(doc, config, environmentLogicalId){
  if(doc.functions) {
    /**
    * Get all the function keys
    */
    def functionKeys = doc.functions.keySet();
    if(functionKeys.size() != 0) {
      for(functionKey in functionKeys) {
        if(doc.functions[functionKey]){
          def funcName = doc.functions[functionKey].name;
          def serviceList;
          def envList;
          if(funcName != null && funcName.contains("-FN_")) {
            /**
            * For old SLS app
            */
            serviceList = config.domain + '_' + config.service + '-'
            envList = '-' + environmentLogicalId
          } else {
            /**
            * For new SLS app
            */
            serviceList = config.domain + '_' + config.service + '_'
            envList = '_' + environmentLogicalId
          }
          /**
          * funcName: jazztest_customnode_function1_sample-dev
          * funcName: jazztest_customnode-FN_function1-sample-dev
          */
          if(funcName != null) {
            funcName = funcName.split(serviceList)[1]
            funcName = funcName.split(envList)[0]
            funcName = funcName.replace("_", "")
            doc.functions[functionKey].name = serviceList + funcName + envList
          }
        }
      }
    }
  }
  return doc
}

/**
* Function for checking Resouce Policy functionKey in user provided serverless.yml
*/
def checkResourcePolicy(doc){
  def isResourcePolicyAvailable = false;
  if(doc.provider.resourcePolicy){
    isResourcePolicyAvailable = true
  }
  return isResourcePolicyAvailable;
}

def checkAndRemovePublicIpInUsersIpList(doc){
  def resourcePolicy = doc.provider.resourcePolicy
  def ipList = resourcePolicy[0]?.Condition?.IpAddress?.get('aws:SourceIp')
  if (ipList)
  {
    println "user ip list => $ipList"

    if(ipList.contains("0.0.0.0/0")) {
      println "Removing public endpoint."
      ipList.remove("0.0.0.0/0")
    }
    println "IP list after checking => $ipList"
    doc.provider.resourcePolicy[0].Condition.IpAddress["aws:SourceIp"] = ipList
    println "IP list after checking inside doc => ${doc.provider.resourcePolicy[0].Condition.IpAddress['aws:SourceIp']}"
  }
  return doc;
}

/*
* check if the arn of the resource is a wildcard
*/
def checkWildcardArn(resourceArn) {
  /*
  * Initializations
  */
  def result = false;
  
  /*
  * Split down the resourceArn and check if the last entity is a wildCard
  */
  def entries = resourceArn.split(':')
  def resourceName = entries[entries.length - 1]

  /*
  * Get the resourceName from the resourceArn
  */
  def resource
  
  // if arn is valid then it should have atleast 6 parts separated by :
  if (resourceArn.split(':').length >= 6)
  {
    resource = resourceArn.split(':')[2]
  }

  if(resource == 'dynamodb'){
    /*
    * For DynamoDB, if the last entity has {table}/ prefix followed by '*', then it is a wildCard
    */
    def resourceNameSplit = resourceName.split("/")
    def resourceNameLastPart = resourceNameSplit[resourceNameSplit.length - 1]

    if(resourceNameLastPart.equals('*')) {
      result = true;
    }

  } else if(resourceName.equals('*')){
    /*
    * For other scenarios, if the last entity of the resourceArn is '*', then it is a wildCard
    */
    result = true
  }

  return result;
}

/*
* Function to check if certain resources with wildCard and actions should be allowed or not
*/
def checkActionsWithWildcardResource(resource, roleStatement) {
  /*
  * Initializations
  */
  def result = true;
  def whitelistValidator = new WhiteListValidatorModule()
  def whiteListYml = whitelistValidator.getWhiteListYml ()
  def isWildCard = false;

  try {
    if(resource){

      if(resource.equals("*")) {
        isWildCard = true
      } else {
        isWildCard = checkWildcardArn(resource)
      }

      /*
      * If Resource is a wildCard, check if the actions are whitelisted
      */
      if(isWildCard){
        def actionList = roleStatement.Action
        def invalidActions = []
        def whitelistedActions = whiteListYml['actionsWithWildcardResource']
        /*
        * Iterate for each action (cloudwatch, lambda, ec2)
        * Action:
          - cloudwatch:PutMetricData
          - lambda:InvokeFunction
          - ec2:CreateNetworkInterface
        */
        for (keyData in actionList) {
          def action = keyData.split(':')[0]
          /*
          * If the action key is among the whitelisted actions, then check the values of the actions
          */
          if(whitelistedActions[action]){
            if(!whitelistedActions[action].contains(keyData.split(':')[1])) {
              invalidActions.push(keyData)
            }
          } else {
            invalidActions.push(keyData)
          }
          /*
          * If any action list falls under non-whitelisted actions, return invalid actions and break out of the loop
          */
          if(invalidActions.size() > 0){
            break;
          }
        }
        if(invalidActions.size() == 0){
          result = false;
        } else {
          println "Your application definition - serverless.yml contains actions ${invalidActions} for the resource ${resource} that are not whitelisted"
        }
      } else {
        result = false;
      }
    }

    return result;
  } catch (e) {
    println "Something went wrong while checking actions with wild card resource: " + e.message
    e.printStackTrace()
    throw new Exception("Something went wrong while checking actions with wild card resource: ", e)
  }

}

/*
* Checking user provided runtime with service config runtime
* with respect to version
*/
def isValidUserRuntime(userProvidedRuntime, serviceConfigRuntime) {
  def runtimes = ['node':['nodejs10.x', 'nodejs12.x'], 'python':['python3.6', 'python3.8'], 'java':['java8', 'java11'], 'go':['go1.x']]
  def listData = runtimes.keySet();
  def validRuntimeValue = false;
  
  userProvidedRuntime = userProvidedRuntime.trim();
  serviceConfigRuntime = serviceConfigRuntime.trim();
  

  for (item in listData) {
    if(userProvidedRuntime.startsWith(item) && serviceConfigRuntime.startsWith(item)) {
      validRuntimeValue = true;
      /*
      * takes care of scenario when user tries to downgrade the runtime
      */
      if(runtimes[item].indexOf(userProvidedRuntime) < runtimes[item].indexOf(serviceConfigRuntime)) {
        validRuntimeValue = false;
      } else {
        validRuntimeValue = true;
      }
      break;
    } else {
      /*
      * takes care of scenario if user tries to update from one runtime to another
      */
      validRuntimeValue = false;
    }
  }
  return validRuntimeValue;

}

/**
* Prepare serverless.yml from
* config
**/
def prepareServerlessYml() {
    def env = System.getenv()
    def props = JSON.getAllProperties()
    def accountDetails = props['accountDetails']
    def serviceConfig = props['serviceConfig']
    def environmentLogicalId = props['environmentLogicalId']
    def deploymentDescriptor = null
    def whitelistValidator = new WhiteListValidatorModule()
    def whiteListYml = whitelistValidator.getWhiteListYml ()
    def commonExcludes = whitelistValidator.getCommonExcludes()
    def isPrimaryAccount = props.configData.AWS.ACCOUNTS.find{ it.ACCOUNTID == serviceConfig.account}?.PRIMARY ? true : false
    
    try {
      // def appContent = new java.io.File("${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME}/application.yml").text
      def appContent = YAML.readFile("${PROPS.WORKING_DIRECTORY}/${props.REPO_NAME}/application.yml")
      println "appContent- $appContent"
      if(!appContent.isEmpty()) {
        println "The application.yml is being used."
        deploymentDescriptor = appContent
      }
    } catch(e) { // TODO to catch the type error
      println "The application.yml does not exist in the code. So the default value from config will be used. $e"
    }

    // Ref: https://ccoe.docs.t-mobile.com/aws/reference/hostname_tagging_guidelines/#environments
    def envTag = "Non-production"
    if (environmentLogicalId == "prod"){
      envTag = "Production"
    }
    println "deploymentDescriptor-: $deploymentDescriptor"
    def doc = deploymentDescriptor ? deploymentDescriptor : [:] // If no descriptor present then simply making an empty one. The readYaml default behavior is to return empty string back that is harful as Map not String is expected below

    println "doc:- $doc"
    // pull VPC subnetIds and securityGroupIds from Account/Region level
    def subnetIds = props.configData.AWS.ACCOUNTS.find{ it.ACCOUNTID == serviceConfig.account }?.REGIONS.find{ it.REGION == serviceConfig.region }?.DEFAULT_SUBNET_IDS
    subnetIds = subnetIds ? subnetIds : [] // if null set it to empty array
    def securityGroupIds = props.configData.AWS.ACCOUNTS.find{ it.ACCOUNTID == serviceConfig.account }?.REGIONS.find{ it.REGION == serviceConfig.region }?.DEFAULT_SECURITY_GROUP_IDS
    securityGroupIds = securityGroupIds ? securityGroupIds : [] // if null set it to empty array

    def logStreamer = props.configData.AWS.KINESIS_LOGS_STREAM

    def destLogStreamArn = props.configData.AWS.ACCOUNTS.find{ it.ACCOUNTID == serviceConfig.account}?.PRIMARY ? logStreamer : 
                            accountDetails.REGIONS.find{it.REGION == serviceConfig.region}.LOGS.PROD
  
    // context available for replacing variables in serverless.yml
    context =["environment_logical_id": environmentLogicalId,
            "INSTANCE_PREFIX": props.configData.INSTANCE_PREFIX,
            "git_commit_hash": props.commitSha,
            "envTag" : envTag,
            "REGION": serviceConfig.region,
            "logRetentionInDays": props.configData.JAZZ.LOGS.CLOUDWATCH_LOG_RETENTION_DAYS,
            "cloud_provider": "aws",
            "subnetIds": subnetIds,
            "securityGroupIds": securityGroupIds,
            "kinesisStreamArn": destLogStreamArn,
            "platformRoleArn": props.configData.AWS.ACCOUNTS.find{ it.ACCOUNTID == serviceConfig.account }.IAM.PLATFORMSERVICES_ROLEID, // pick the role for selected account
            "serverless_framework_version": ">=1.0.0 <3.0.0"]
    

    /**
    * Checking for http events in user provided serverless.yml
    */
    def isHttpEvent = checkForHttpEvents(doc);
    checkForHttpApiEvents(doc);
    def isResourcePolicy
    def isPrivateEndpoint = false;
    if(doc && doc.provider && doc.provider.endpointType && doc.provider.endpointType == 'PRIVATE'){
      isPrivateEndpoint = true;
    }
    /**
    * If serverless.yml has http event and if user provides resourcePolicy
    * it will use users' ip values 
    */
    if(isHttpEvent){
      /**
      * Checking if user has ResourcePolicy in serverless.yml
      */
      isResourcePolicy = checkResourcePolicy(doc);
      if(isResourcePolicy){
        /**
        * Providing config Ip Values for resourcePolicy
        * If user provides resource policy, it will use user config values
        */       
        serviceConfig['ipList'] = props.configData.JAZZ.INTERNAL_IP_ADDRESSES
        //Allow 0.0.0.0/0 in serverless.yml only when service is marked with is_public_endpoint: true flag
        if(!serviceConfig.is_public_endpoint) {
          println "public endpoint is false. So checking public ip in resource policy."
          doc = checkAndRemovePublicIpInUsersIpList(doc)
        }
      }
    } else {
      serviceConfig['ipList'] = []
    }

    serviceConfig['commonExcludes'] = commonExcludes;
    // remove service from yaml as it will be overriden by platform
    if (doc && doc instanceof Map && doc['service']) doc.remove('service')
    // remove frameworkVersion from yaml as it will be overriden by platform
    if (doc && doc instanceof Map && doc['frameworkVersion']) doc.remove('frameworkVersion')

    def rules = YAML.readFile("${PROPS.WORKING_MODULE_DIRECTORY}/custom/sls/serverless-build-rules.yml")
    def resultingDoc = processServerless(doc,
                                          rules,
                                          serviceConfig,
                                          context)
    def isApiProxyPlugin = false;
    if(resultingDoc.plugins && resultingDoc.plugins.contains('serverless-apigateway-service-proxy')){
      isApiProxyPlugin = true;
    }
    /*
    * Checking user provided runtime with service config default runtime
    * Only if user provides one
    */
    if(resultingDoc.provider['runtime']) {
      def runtimeCheckResult = isValidUserRuntime(resultingDoc.provider.runtime, serviceConfig.providerRuntime);
      if(!runtimeCheckResult) {
        throw new Exception("Runtime update is not allowed from: ${serviceConfig.providerRuntime} to ${resultingDoc.provider.runtime}" )
      }
    }
    /**
    * If user doesn't have ResourcePolicy, we are manually integrating in the serverless.yml
    */
    if((isHttpEvent && !isResourcePolicy && !isPrivateEndpoint) || (isApiProxyPlugin && !isResourcePolicy)){
      isResourcePolicy = checkResourcePolicy(doc);
      def compiledIpValues = props.configData.JAZZ.INTERNAL_IP_ADDRESSES
      /*
      * If user has resourcePolicy, then merging it as well
      */
      if(resultingDoc.provider.resourcePolicy) {
        def ipList = resultingDoc.provider.resourcePolicy[0]?.Condition?.IpAddress?.get('aws:SourceIp')
        if(isResourcePolicy && ipList) {
          for (userIps in ipList) {
            if(userIps) {
              compiledIpValues.push(userIps)
            }
          }
        }
      }
      println "compiledIpValues: ${compiledIpValues}"
      def ipValues = ['aws:SourceIp': compiledIpValues]
      resultingDoc.provider['resourcePolicy'] = [["Effect":"Allow", "Principal": '*', "Action": "execute-api:Invoke", "Resource": ["execute-api:/*/*/*"], "Condition": ["IpAddress": ipValues]]]
    }

    resultingDoc.provider['logRetentionInDays'] = props.configData.JAZZ.LOGS.CLOUDWATCH_LOG_RETENTION_DAYS

    resultingDoc = removeUnderscoreInFunctionName(resultingDoc, serviceConfig, environmentLogicalId)

    // Supplying the Outputs element to render all arns for all 'resources/Resources that got listed'
    def smallResourcesElem = resultingDoc['resources']
    if(smallResourcesElem) {
      def bigResourcesElem = smallResourcesElem['Resources']
      if(bigResourcesElem) {
        def bigOutputsElem = smallResourcesElem['Outputs']
        if(!bigOutputsElem) {
          bigOutputsElem = [:]
          smallResourcesElem['Outputs'] = bigOutputsElem
        }
        def resourceKeys = bigResourcesElem.collect{key, val -> key}
        def outputResources = smallResourcesElem['Resources']
        def whitelistedAssetTypes = whiteListYml['assetTypes']
        
        /* Forming a record that extracts the arn from resulting item for each of resource key extracted from resources:
        UsersTableArn:\n
        Value:\n
        \"Fn::GetAtt\": [ usersTable, Arn ]\n
        */
        
        resourceKeys.collect{name ->
          def resourceType = outputResources[name]['Type']
          if (whitelistedAssetTypes.contains(resourceType)) {
            if (resourceType.equals("AWS::CloudFront::Distribution")) {
              bigOutputsElem[name+'DomainName']=["Value":["Fn::GetAtt":[name, "DomainName"]]]
            } else {
              bigOutputsElem[name+'Arn']=["Value":["Fn::GetAtt":[name, "Arn"]]]
            }
          }
      }
    }
  }

    // inject log subscription ALWAYS - TODO implement in SBR
  	def logSubscriptionMap = [logSubscription:[enabled:true, destinationArn:context.kinesisStreamArn]]
  	if(isPrimaryAccount) logSubscriptionMap.logSubscription['roleArn'] = context.platformRoleArn
  
    if (resultingDoc?.custom)
    {
        // overwriting if exists
        resultingDoc.custom.logSubscription = logSubscriptionMap.logSubscription
    }
    else
    {
        // setting it
        resultingDoc.custom = logSubscriptionMap
    }

    // inject provider:vpc block if require_internal_access = true && there is no vpc already - TODO implement in SBR
    if (serviceConfig.require_internal_access && !resultingDoc?.provider?.vpc)
    {
        // remove vpc block from provider
        if (resultingDoc?.provider)
        {
            def vpcMap = [securityGroupIds: securityGroupIds, subnetIds: subnetIds]
            resultingDoc.provider.vpc = vpcMap
        }
    }
    // check provider IAM Role Statements for Resource = "*"  - TODO implement in SBR
    if (resultingDoc.provider?.iamRoleStatements)
    {
        // iterate through
        resultingDoc.provider.iamRoleStatements.each { roleStatement ->
          if (roleStatement.Resource){
            if (roleStatement.Resource instanceof Collection) {
              roleStatement.Resource.each { eachResource ->
                if (eachResource instanceof Collection) {
                  eachResource.each { eachResourceItem ->
                    if (eachResourceItem?.trim() && checkActionsWithWildcardResource(eachResourceItem.trim(), roleStatement)){
                      throw new IllegalStateException("Your application definition - serverless.yml contains a wild-card Resource ${roleStatement} that is not supported.");
                    }
                  }
                }
                else if (eachResource instanceof Map){
                  // ignore map as that is usually a function
                }
                else if (eachResource?.trim() && checkActionsWithWildcardResource(eachResource.trim(), roleStatement)){
                  throw new IllegalStateException("Your application definition - serverless.yml contains a wild-card Resource ${roleStatement} that is not supported.");
                }
              }
            }
            else if (roleStatement.Resource?.trim() && checkActionsWithWildcardResource(roleStatement.Resource.trim(), roleStatement)){
              throw new IllegalStateException("Your application definition - serverless.yml contains a wild-card Resource ${roleStatement} that is not supported.");
            }
          }
        }
    }
    // check resources Policies for AWS::IAM::Role - TODO implement in SBR
    if (resultingDoc.resources?.Resources)
    {
        resultingDoc.resources?.Resources.each { name, resource ->
            if (resource.Type?.equals("AWS::IAM::Role"))
            {
                if (resource.Properties?.Policies)
                {
                    resource.Properties?.Policies.each { policy ->
                        if (policy.PolicyDocument?.Statement)
                        {
                            policy.PolicyDocument.Statement.each { statement ->
                                if (statement.Resource) {
                                    if (statement.Resource instanceof Collection) {
                                        statement.Resource.each { eachResource ->
                                            if (eachResource.trim() && checkActionsWithWildcardResource(eachResource.trim(), statement)){
                                                throw new IllegalStateException("Your application definition - serverless.yml contains a wild-card Resource ${resource} that is not supported.");
                                            }
                                        }
                                    } else if (statement.Resource?.trim() && checkActionsWithWildcardResource(eachResource.trim(), statement)){
                                        throw new IllegalStateException("Your application definition - serverless.yml contains a wild-card Resource ${resource} that is not supported. Please refer to documentation for supported Resource values.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return resultingDoc
}
