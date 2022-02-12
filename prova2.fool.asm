push 0
push 1
push 5
add
push 5
push 4
sub
push 5
push 2
mult
push 4
push 2
div
lfp
push -3
add
lw
lfp
push -5
add
lw
bleq label0
push 0
b label1
label0:
push 1
label1:
lfp
push -6
add
lw
push 0
beq label2
push 0
b label3
label2:
push 1
label3:
print
halt