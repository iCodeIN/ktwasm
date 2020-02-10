(module
  (global $g (mut i32) (i32.const 0))
  (func (export "add_global") (param $p i32) (result i32)
    (global.get $g)
    (local.get $p)
    (i32.add))
  (export "g" (global $g)))
