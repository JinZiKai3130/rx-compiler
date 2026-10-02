# Kotlin compiler build. Included by the root Makefile; standalone: `make -f compiler.mk compiler`
#
# Pipeline (everything lands in build/, which is gitignored):
#   grammar/{Lexer,Parser}.g4
#     ~> build/antlr-src/Rx{Lexer,Parser}.g4   (renamed: Lexer/Parser clash with the ANTLR runtime)
#     ~> build/antlr-src/*.java                (antlr4 -Dlanguage=Java -visitor)
#     ~> build/classes                         (javac)
#     ~> build/kotlin-classes                  (kotlinc on src/main/kotlin)
#   Run the compiler with ./scripts/rxc

ANTLR_JAR  ?= $(HOME)/tools/antlr/antlr-4.13.2-complete.jar
ANTLR4     ?= antlr4
JAVAC      ?= javac
KOTLINC    ?= kotlinc

KOTLIN_SRC := $(shell find src/main/kotlin -name '*.kt' 2>/dev/null)
GEN_DIR    := build/antlr-src
JAVA_OUT   := build/classes
KOTLIN_OUT := build/kotlin-classes

.PHONY: compiler compiler-clean

# Build the whole compiler and the ./scripts/rxc launcher.
compiler: $(KOTLIN_OUT)/.built scripts/rxc
	@chmod +x scripts/rxc
	@echo "[compiler] built; run: ./scripts/rxc <file.rx>"

# 1) grammar copies with clash-free names
$(GEN_DIR)/RxLexer.g4: grammar/Lexer.g4
	@mkdir -p $(GEN_DIR)
	sed -e 's/^lexer grammar Lexer;/lexer grammar RxLexer;/' $< > $@

$(GEN_DIR)/RxParser.g4: grammar/Parser.g4 $(GEN_DIR)/RxLexer.g4
	@mkdir -p $(GEN_DIR)
	sed -e 's/^parser grammar Parser;/parser grammar RxParser;/' \
	    -e 's/tokenVocab=Lexer;/tokenVocab=RxLexer;/' $< > $@

# 2) generate Java sources into package rx.gen (a default-package class cannot be
#    referenced from Kotlin named packages); lexer first: parser needs RxLexer.tokens
$(GEN_DIR)/.antlr.stamp: $(GEN_DIR)/RxLexer.g4 $(GEN_DIR)/RxParser.g4
	cd $(GEN_DIR) && $(ANTLR4) -Dlanguage=Java -package rx.gen -visitor RxLexer.g4
	cd $(GEN_DIR) && $(ANTLR4) -Dlanguage=Java -package rx.gen -visitor RxParser.g4
	@touch $@

# 3) compile generated Java
$(JAVA_OUT)/.built: $(GEN_DIR)/.antlr.stamp
	@mkdir -p $(JAVA_OUT)
	$(JAVAC) -cp $(ANTLR_JAR) -d $(JAVA_OUT) $(GEN_DIR)/*.java
	@touch $@

# 4) compile Kotlin sources against the generated parser classes
$(KOTLIN_OUT)/.built: $(JAVA_OUT)/.built $(KOTLIN_SRC)
	@mkdir -p $(KOTLIN_OUT)
	$(KOTLINC) $(KOTLIN_SRC) -cp $(JAVA_OUT):$(ANTLR_JAR) -d $(KOTLIN_OUT)
	@touch $@

compiler-clean:
	rm -rf $(GEN_DIR) $(JAVA_OUT) $(KOTLIN_OUT)
	@echo "[compiler] cleaned"
