push 0
push 1
push 5
add
push 5
push 1
sub
push 2
push 5
mult
push 4
push 2
div
lfp
push -3
add
lw
lfp
push -2
add
lw
bleq label4
push 0
b label5
label4:
push 1
label5:
push 0
beq label2
push 0
b label3
label2:
push 1
label3:
push 1
beq label0
lfp
push -5
add
lw
lfp
push -2
add
lw
add
b label1
label0:
lfp
push -4
add
lw
lfp
push -5
add
lw
div
label1:
print
halt