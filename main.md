```html
<script>console.log("XSS Test 1")</script>
<img src="x" onerror="console.log('XSS Test 2')">
<a href="javascript:console.log('XSS Test 3')">Click me</a>
<div onmouseover="console.log('XSS Test 4')">Hover me!</div>
<svg onload="console.log('XSS Test 5')"></svg>
<iframe src="javascript:console.log('XSS Test 6')"></iframe>
<img src=12345 onerror="console.log(1)">
```

