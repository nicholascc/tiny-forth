# tiny-forth

A simple stack-based programming language written in ClojureScript to run in the browser. The non-boilerplate portion of the codebase, including the editor, interpreter, and parser, is less than 150 lines of code.

The website editor looks like this, and includes a saving mechanism with `localStorage`. Below the program input is the stack at the end of the program's execution, and the program's output (through the command `print`). 

<img src="https://user-images.githubusercontent.com/34197135/133296453-ab9b6255-9710-469c-b1e6-f79f67277a80.png" alt="An image of the browser-based editor, showing a text box for entering the program, a button to run the program, and a text-based display of the output of the program and the stack at the end of the program's execution." height="400">

## Installation

Clone this repository, install [leiningen](https://leiningen.org/) and [shadow-cljs](https://github.com/thheller/shadow-cljs), then run the project in a hot-reloading development mode with `shadow-cljs watch app`.

## Programming Language

### Commands Overview

The language is entirely centered around the stack, and instructions are therefore written in reverse polish notation. Numbers are added to the beginning of the stack:

```
3 2 1
 => 1 2 3
```

(Note that in this language a stack is written with the top element written first).

The experssion (3 + 4) - (2 * 5) is written like so:
```
3 4 + 2 5 * -
 => 4
```

The division and modulus operators are also available as `/` and `mod` respectively.
Additionally, the boolean operators `=`, `>`, `<`, `and`, `or`, and `not` are in the language. `true` adds a boolean true to the stack, and `false` adds false to the stack.

`print` will print the top item of the stack to the program output.

`dup` duplicates the first item on the stack, `swap` swaps the positions of the first two items of the stack, and `del` removes the first item on the stack without doing anything.

```
2 3 4 del swap dup
 => 2 2 3
```

The core abstraction of this language is the ability to store stacks as values inside the stack or inside other stacks. For example:

```
1 [2 3 [4 5] 6] 7
 => 7 [2 3 [4 5] 6] 1
```

These stacks can be `concatenated` together, and items can be `push`ed and `pop`ped from them

```
[3] 2 push [4 5] concat pop
 => 4 [5 2 3]
```

`collect` gives you a new stack whose only element is the current stack
```
2 3 4 + collect [5 6] concat
 => [5 6 7 2]
```

`collect-n` makes a new stack out of the top n items of the stack, where n is the first item on the stack

```
1 2 3 4 2 collect-n
 => [4 3] 2 1
```

`drop` takes a stack and places all its elements on the top of the main stack.

```
10 11 [1 2 3] drop
 => 1 2 3 11 10
```

`eval` evaluates the top element of the stack as if it were its own program, and then once that program's execution is complete, returns to the normal program.

```
15 3 [dup +] eval -
 => 9
```

`eval-with` evaluates the top element of the stack as if it were its own program, starting with the second element of the stack as the stack. Once the program's evaluation is complete, the execution's final stack is added to the top of the stack. (This may be confusing, look at the example to get a better idea):

```
10 [2 3] [dup +] eval-with
 => [4 3] 10
```

`'` simply allows you to add the following 'word' to the stack without executing it. So for example:

```
3 ' eval ' + swap ' drop
 => drop eval + 3
```

This is useful for constructing instructions for `eval` or for defining new procedures like `concat`, `+`, etc. using `defproc`. `defproc` takes a stack representing a program and a word, and causes a change in the state of the program such that if you use that word later in the program, it will execute the program you defined it to represent.

```
[dup *] ' square defproc

2 square 3 square *

=> 36
```

`choose` will result in the second item on the stack if the first item is `true`, and will result in the third item on the stack if the first item is `false`.

```
1 2 false choose
 => 1

1 2 true choose
 => 2
```

### Utility procedures

`choose`, in combination with `defproc` and `eval`, allows us to make an `if` statement:

```
[choose eval] ' if defproc

3 [dup +] [dup *] false if
4 [dup +] [dup *] true if
 => 16 6
```

Also, we can make a useful command which takes the nth element on the stack and 'pulls' it to the top of the stack.

```
[collect-n swap push drop] ' pull defproc

8 7 6 5 4 3 2 1
3 pull
 => 4 1 2 3 5 6 7 8
```

This lets us make an if statement that is a little bit more useful, where the condition comes before the clauses:

```
[2 pull choose eval] ' if defproc

4 dup 3 > [dup +] [dup *] if
 => 16
```

This definition of `if` will be the one we use throughout the rest of this README.

A simple while loop can be written like so:

```
[
dup
2 pull
swap
eval-with
pop
[swap del drop]
[swap while]
if
]
' while defproc
```

The input is a stack which is the starting stack of the loop and a body, which is evaluated with the stack. Each time the body is evaluated with the stack the first element of the final stack is removed and is treated as a boolean for whether or not to continue. For example, the following program prints the squares of the numbers from 1 to 10:

```
[1] [1 + dup dup * print dup 11 <] while
 |=> 1
 |=> 4
 |=> 9
 |=> 16
 |=> 25
 |=> 36
 |=> 49
 |=> 64
 |=> 81
 |=> 100
 => 11
```

And the following program implements [Fizz Buzz](https://rosettacode.org/wiki/FizzBuzz) from 0 to 50:

```
[0]
[
 dup print
 dup 3 mod 0 =
 swap dup 5 mod 0 =
 2 pull
 and
 
 [
  dup 3 mod 0 =
  [] [' fizz print] if
  dup 5 mod 0 =
  [] [' buzz print] if
 ] 
 [' fizzbuzz print]
 if
 
 1 + dup 50 <
] while
```
