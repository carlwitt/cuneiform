/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Jörgen Brandt (HU Berlin)
 * Marc Bux (HU Berlin)
 * Ulf Leser (HU Berlin)
 *
 * Jörgen Brandt is funded by the European Commission through the BiobankCloud
 * project. Marc Bux is funded by the Deutsche Forschungsgemeinschaft through
 * research training group SOAMED (GRK 1651).
 *
 * Copyright 2014 Humboldt-Universität zu Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

grammar Cuneiform ;


// NON-TERMINAL SYMBOLS

script           : stat* EOF ;

// the body
stat             : target       // top-level
                 | importFile
                 | instat
                 | expr
                   { notifyErrorListeners( "Dangling expression. Expecting ';' or '-+'." ); }
                 ;

instat           : assign       // allowed also in blocks
                 | defTask
                 | danglingExpr
                 ;

// imports
importFile       : ( IMPORT | INCLUDE )STRING SEMICOLON ;

// assignments, tasks and lambda expressions
assign           : name+ EQUAL expr SEMICOLON ;

defTask          : DEFTASK ID prototype LBRACE instat+ RBRACE # NativeDefTask
                 | DEFTASK ID prototype foreignBody           # ForeignDefTask
                 | DEFTASK
                   { notifyErrorListeners( "Incomplete Task definition. Task name expected." ); } # DefTaskErr1
                 | DEFTASK ID prototype LBRACE
                   { notifyErrorListeners( "Missing '}'." ); } # DefTaskErr2
                 | DEFTASK ID prototype LBRACE RBRACE
                   { notifyErrorListeners( "Empty native task block." ); } # DefTaskErr3
                 | DEFTASK ID LPAREN
                   { notifyErrorListeners( "Incomplete task prototype. Expecting at least one output variable declaration." ); } # FnPrototypeErr1
                 | DEFTASK ID LPAREN output+ COLON param*
                   { notifyErrorListeners( "Incomplete task prototype. Expecting input parameter declaration or ')'." ); } # FnPrototypeErr2
                 | DEFTASK ID LPAREN output+ COLON param* RPAREN RPAREN+
                   { notifyErrorListeners( "Too many ')'." ); } # FnPrototypeErr3
                 ;

// type system
prototype        : LPAREN output+ COLON param* RPAREN ;

name             : ID                                 # NameInferredType
                 | ID LPAREN ID RPAREN                # NameDataType
                 | ID LPAREN COLON RPAREN             # NamePlainFnType
                 | ID prototype                       # NameDeepFnType
                 | ID LPAREN COLON?
                   { notifyErrorListeners( "Missing ')'." ); } # NameErr1
                 | ID LPAREN ID RPAREN RPAREN+
                   { notifyErrorListeners( "Too many ')'." ); } # NameErr2
                 ;

param            : name
                 | reduceVar
                 | correlParam
                 | draw
                 ;

draw             : LBRACE COMB INT COLON( name )RBRACE  # DrawComb
                 | LBRACE COMBR INT COLON( name )RBRACE # DrawCombr
                 | LBRACE VAR INT COLON( name )RBRACE   # DrawVar
                 | LBRACE PERM INT COLON( name )RBRACE  # DrawPerm
                 ;

output           : name
                 | reduceVar
                 ;

reduceVar        : LTAG name RTAG ;

correlParam      : LSQUAREBR name name+ RSQUAREBR                  
                 | LSQUAREBR name RSQUAREBR
                   { notifyErrorListeners( "Correlated parameter list must have at least two entries." ); }
                 | LSQUAREBR RSQUAREBR
                   { notifyErrorListeners( "Empty correlated parameter list." ); }
                 ;

// expressions
expr             : NIL         # NilExpr
                 | singleExpr+ # CompoundExpr
                 ;

danglingExpr     : expr TOSTACK ;

singleExpr       : ID                                                     # IdExpr
                 | INT                                                    # IntExpr
                 | STRING                                                 # StringExpr
                 | FROMSTACK                                              # FromStackExpr
                 | IF expr THEN expr ELSE expr END                        # CondExpr
                 | channel? APPLY LPAREN paramBind+ TILDE? RPAREN         # ApplyExpr
                 | channel? ID LPAREN paramBind* TILDE? RPAREN            # CallExpr
                 | CURRY LPAREN paramBind+ RPAREN                         # CurryExpr
                 | LAMBDA prototype block                                 # NativeLambdaExpr
                 | LAMBDA prototype foreignBody                           # ForeignLambdaExpr
                 | APPLY
                   { notifyErrorListeners( "Incomplete task application. Missing '('." ); } # SingleExprErr1
                 | APPLY LPAREN
                   { notifyErrorListeners( "Incomplete task application. Missing Parameter bindings, e.g. 'param: value'." ); } # SingleExprErr2
                 | APPLY LPAREN paramBind+
                   { notifyErrorListeners( "Incomplete task application. Missing ')'." ); } # SingleExprErr3
                 | APPLY LPAREN ID COLON
                   { notifyErrorListeners( "Incomplete Parameter binding. Missing value." ); } # ParamBindErr1
                 | LAMBDA prototype INLANG OPENBODY
                   { notifyErrorListeners( "In foreign task definition: Missing '}*'." ); } # ForeignFnBodyErr2
                 ;

channel          : LSQUAREBR INT RSQUAREBR ;

block            : LBRACE instat+ RBRACE
                 | LBRACE RBRACE
                   { notifyErrorListeners( "Empty block. Expecting target, assignment, or task definition." ); }
                 ;
               
paramBind        : ID COLON expr ;

target           : expr SEMICOLON ;

foreignBody      : INLANG BODY ;

// TERMINAL SYMBOLS

APPLY            : 'apply' ;
COLON            : ':' ;
COMB             : 'comb' ;
COMBR            : 'combr' ;
CURRY            : 'curry' ;
DEFTASK          : 'deftask' ;
ELSE             : 'else' ;
END              : 'end' ;
EQUAL            : '=' ;
FROMSTACK        : '<' '-'+ '+' ;
IF               : 'if' ;
INLANG           : 'in' WSSYMB+ LANGSYMB ;
fragment LANGSYMB: 'bash' | 'r' | 'R' | 'lisp' | 'octave' | 'matlab' | 'perl'
                 | 'pegasus' | 'python' ;
IMPORT           : 'import' ;
INCLUDE          : 'include' ;
LAMBDA           : '\\' ;
LBRACE           : '{' ;
LPAREN           : '(' ;
LSQUAREBR        : '[' ;
LTAG             : '<' ;
NIL              : 'nil' ;
PERM             : 'perm' ;
RBRACE           : '}' ;
RPAREN           : ')' ;
RSQUAREBR        : ']' ;
RTAG             : '>' ;
SEMICOLON        : ';' ;
TILDE            : '~' ;
THEN             : 'then' ;
TOSTACK          : '-'+ '+' ;
VAR              : 'var' ;

INT              : [0-9]
                 | '-'? [1-9][0-9]*
                 ;
BODY             : LMMECB .*? RMMECB ;
OPENBODY         : LMMECB .*? ;
fragment LMMECB  : '*{' ;
fragment RMMECB  : '}*' ;
STRING           : '\'' ( '\\\'' | '\\\\' | . )*? '\''
                 | '"' ( '\\"' | '\\\\' | . )*? '"'
                 ;
COMMENT          : ( ( '#' | '//' | '%' ) ~'\n'*
                 | '/*' .*? '*/'
                 | '<!--' .*? '-->'
                 | '|' ) -> skip
                 ;
ID               : [a-zA-Z0-9\.\-_\+\*/]+ ;
WS               : WSSYMB -> channel( HIDDEN ) ;
fragment WSSYMB  : [ \n\r\t,] ;
