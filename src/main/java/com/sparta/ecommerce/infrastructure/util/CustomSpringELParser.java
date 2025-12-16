package com.sparta.ecommerce.common.util;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;


/**
 * Spring Expression Language Parser
 * 어노테이션의 key 값을 파싱하여 실제 값으로 변환
 */
public class CustomSpringELParser {

    private CustomSpringELParser(){
    }

    public static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length ; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context,Object.class);
    }
}
